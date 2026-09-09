from datetime import timedelta
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from flask import Blueprint, current_app, jsonify, request, send_from_directory

from ..database import get_db
from ..models import CallJob
from ..services.llm_service import process_message
from ..services.tts_service import generate_audio
from ..tasks.call_task import execute_call
from ..utils.helpers import (
    ensure_ist_datetime,
    get_current_ist,
    is_at_least_two_minutes_ahead,
    validate_indian_number,
)


calls_bp = Blueprint("calls", __name__)
GENERATED_AUDIO_DIR = Path(__file__).resolve().parents[2] / "generated_audio"


@dataclass
class ScheduleCallRequest:
    user_id: str
    contact_name: str
    contact_number: str
    message: str
    user_name: str
    chosen_time: Optional[str] = None

    @classmethod
    def from_dict(cls, payload: dict):
        required_fields = ["user_id", "contact_name", "contact_number", "message", "user_name"]
        missing_fields = [field for field in required_fields if not str(payload.get(field, "")).strip()]
        if missing_fields:
            raise ValueError(f"Missing required fields: {', '.join(missing_fields)}")
        return cls(
            user_id=str(payload["user_id"]).strip(),
            contact_name=str(payload["contact_name"]).strip(),
            contact_number=str(payload["contact_number"]).strip(),
            message=str(payload["message"]).strip(),
            user_name=str(payload["user_name"]).strip(),
            chosen_time=str(payload["chosen_time"]).strip() if payload.get("chosen_time") else None,
        )


def _schedule_job(job_id: str, scheduled_time) -> None:
    scheduler = current_app.extensions["scheduler"]
    scheduler.add_job(
        func=execute_call,
        trigger="date",
        run_date=scheduled_time,
        args=[job_id],
        id=job_id,
        replace_existing=True,
        misfire_grace_time=3600,
    )


def _resolve_scheduled_time(chosen_time: Optional[str], llm_result: dict):
    if chosen_time:
        return ensure_ist_datetime(chosen_time), True
    if llm_result.get("time_extracted") and llm_result.get("scheduled_time"):
        return ensure_ist_datetime(llm_result["scheduled_time"]), True
    return None, False


def _is_public_url(value: str) -> bool:
    normalized_value = str(value or "").strip().lower()
    return normalized_value.startswith("http://") or normalized_value.startswith("https://")


def _retry_existing_job(call_job: CallJob):
    if not call_job.audio_url:
        raise ValueError("This call cannot be retried yet because it does not have a hosted audio URL to reuse.")

    retried_time = get_current_ist() + timedelta(minutes=2, seconds=10)
    call_job.scheduled_time = retried_time
    call_job.status = "pending"
    call_job.msg91_request_id = None
    _schedule_job(call_job.id, retried_time)
    return retried_time


@calls_bp.get("/")
def test_frontend():
    # Flask serves files from app/static automatically, so we only need to point root at index.html.
    return current_app.send_static_file("index.html")


@calls_bp.get("/media/<path:filename>")
def serve_generated_audio(filename: str):
    # send_from_directory applies a safe path join so callers can only access files inside generated_audio.
    current_app.logger.info("Serving generated audio file %s from the public media route.", filename)
    return send_from_directory(GENERATED_AUDIO_DIR, filename, conditional=True)


@calls_bp.get("/health")
def health_check():
    return jsonify({"status": "ok"}), 200


@calls_bp.post("/schedule-call")
def schedule_call():
    payload = request.get_json(silent=True) or {}
    try:
        schedule_request = ScheduleCallRequest.from_dict(payload)
    except ValueError as exc:
        return jsonify({"status": "error", "message": str(exc)}), 400

    if not validate_indian_number(schedule_request.contact_number):
        return jsonify({"status": "error", "message": "contact_number must be in +91XXXXXXXXXX format."}), 400

    current_time_ist = get_current_ist()
    llm_result = process_message(
        message=schedule_request.message,
        user_name=schedule_request.user_name,
        contact_name=schedule_request.contact_name,
        current_time_ist=current_time_ist,
    )
    message_preview = llm_result["rephrased_message"]

    try:
        scheduled_time, time_available = _resolve_scheduled_time(schedule_request.chosen_time, llm_result)
    except ValueError as exc:
        return jsonify({"status": "error", "message": str(exc)}), 400

    if not time_available or scheduled_time is None:
        return (
            jsonify(
                {
                    "status": "needs_time",
                    "message_preview": message_preview,
                    "time_extracted": False,
                }
            ),
            200,
        )

    if not is_at_least_two_minutes_ahead(scheduled_time):
        return (
            jsonify(
                {
                    "status": "error",
                    "message": "Scheduled time must be at least 2 minutes ahead of the current IST time.",
                }
            ),
            400,
        )

    try:
        # Pre-generating audio at scheduling time makes the later Exotel call much more reliable.
        audio_url = generate_audio(message_preview)
    except Exception:
        current_app.logger.exception("Failed to generate hosted audio during schedule-call.")
        return jsonify({"status": "error", "message": "Unable to generate the call audio right now."}), 502

    if not _is_public_url(audio_url):
        return (
            jsonify(
                {
                    "status": "error",
                    "message": "Audio was generated locally, but PUBLIC_BASE_URL is not configured for a public hosted file URL.",
                }
            ),
            500,
        )

    db_session = get_db()
    try:
        call_job = CallJob(
            user_id=schedule_request.user_id,
            contact_name=schedule_request.contact_name,
            contact_number=schedule_request.contact_number,
            original_message=schedule_request.message,
            rephrased_message=message_preview,
            audio_url=audio_url,
            scheduled_time=scheduled_time,
            status="pending",
        )
        db_session.add(call_job)
        db_session.commit()
        db_session.refresh(call_job)
        _schedule_job(call_job.id, scheduled_time)
    except Exception:
        db_session.rollback()
        current_app.logger.exception("Failed to save and schedule a call job.")
        return jsonify({"status": "error", "message": "Unable to schedule the call right now."}), 500
    finally:
        db_session.close()

    return (
        jsonify(
            {
                "status": "scheduled",
                "job_id": call_job.id,
                "call_time": scheduled_time.isoformat(),
                "message_preview": message_preview,
                "audio_url": audio_url,
                "time_extracted": bool(llm_result.get("time_extracted") or schedule_request.chosen_time),
            }
        ),
        201,
    )


@calls_bp.get("/calls")
def get_calls():
    user_id = str(request.args.get("user_id", "")).strip()
    if not user_id:
        return jsonify({"status": "error", "message": "user_id query parameter is required."}), 400

    db_session = get_db()
    try:
        jobs = (
            db_session.query(CallJob)
            .filter(CallJob.user_id == user_id)
            .order_by(CallJob.scheduled_time.desc())
            .all()
        )
    finally:
        db_session.close()

    upcoming = [job.to_dict() for job in jobs if job.status == "pending"]
    past = [job.to_dict() for job in jobs if job.status != "pending"]
    return jsonify({"upcoming": upcoming, "past": past}), 200


@calls_bp.delete("/calls/<job_id>")
def cancel_call(job_id: str):
    db_session = get_db()
    try:
        call_job = db_session.query(CallJob).filter(CallJob.id == job_id).first()
        if call_job is None:
            return jsonify({"status": "error", "message": "Call job not found."}), 404
        if call_job.status != "pending":
            return jsonify({"status": "error", "message": "Only pending calls can be cancelled."}), 400

        scheduler = current_app.extensions["scheduler"]
        if scheduler.get_job(job_id):
            scheduler.remove_job(job_id)

        call_job.status = "cancelled"
        db_session.commit()
    except Exception:
        db_session.rollback()
        current_app.logger.exception("Failed to cancel call job %s.", job_id)
        return jsonify({"status": "error", "message": "Unable to cancel the call right now."}), 500
    finally:
        db_session.close()

    return jsonify({"status": "cancelled", "job_id": job_id}), 200


@calls_bp.post("/calls/<job_id>/retry")
def retry_call(job_id: str):
    db_session = get_db()
    try:
        call_job = db_session.query(CallJob).filter(CallJob.id == job_id).first()
        if call_job is None:
            return jsonify({"status": "error", "message": "Call job not found."}), 404
        if call_job.status not in {"failed", "cancelled"}:
            return jsonify({"status": "error", "message": "Only failed or cancelled calls can be retried."}), 400

        retried_time = _retry_existing_job(call_job)
        db_session.commit()
    except ValueError as exc:
        db_session.rollback()
        return jsonify({"status": "error", "message": str(exc)}), 400
    except Exception:
        db_session.rollback()
        current_app.logger.exception("Failed to retry call job %s.", job_id)
        return jsonify({"status": "error", "message": "Unable to retry the call right now."}), 500
    finally:
        db_session.close()

    return (
        jsonify(
            {
                "status": "scheduled",
                "job_id": job_id,
                "call_time": retried_time.isoformat(),
                "message": "Call retried using the existing generated audio.",
            }
        ),
        200,
    )
