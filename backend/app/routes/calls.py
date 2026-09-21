from datetime import timedelta
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from flask import Blueprint, current_app, jsonify, request, send_from_directory

from ..database import get_db
from ..models import CallJob
from ..services.llm_service import normalize_indian_number, process_message
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
    user_name: str
    raw_prompt: str
    contacts: list[dict]
    chosen_time: Optional[str] = None
    confirmed_number: Optional[str] = None
    confirmed_contact_name: Optional[str] = None
    confirmed_message: Optional[str] = None
    confirmed: bool = False
    agent_result: Optional[dict] = None

    @classmethod
    def from_dict(cls, payload: dict):
        required_fields = ["user_id", "user_name", "raw_prompt"]
        missing_fields = [field for field in required_fields if not str(payload.get(field, "")).strip()]
        if missing_fields:
            raise ValueError(f"Missing required fields: {', '.join(missing_fields)}")
        contacts = payload.get("contacts", [])
        if not isinstance(contacts, list) or any(not isinstance(contact, dict) for contact in contacts):
            raise ValueError("contacts must be a list of objects containing name and number fields.")
        agent_result = payload.get("agent_result")
        if agent_result is not None and not isinstance(agent_result, dict):
            raise ValueError("agent_result must be an object when provided.")
        return cls(
            user_id=str(payload["user_id"]).strip(),
            user_name=str(payload["user_name"]).strip(),
            raw_prompt=str(payload["raw_prompt"]).strip(),
            contacts=contacts,
            chosen_time=str(payload["chosen_time"]).strip() if payload.get("chosen_time") else None,
            confirmed_number=(
                str(payload["confirmed_number"]).strip() if payload.get("confirmed_number") else None
            ),
            confirmed_contact_name=(
                str(payload["confirmed_contact_name"]).strip()
                if payload.get("confirmed_contact_name")
                else None
            ),
            confirmed_message=(
                str(payload["confirmed_message"]).strip() if payload.get("confirmed_message") else None
            ),
            confirmed=payload.get("confirmed") is True,
            agent_result=agent_result,
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


def _build_spoken_message(user_name: str, contact_name: str, message: str) -> str:
    cleaned_message = " ".join(str(message or "").strip().split())
    prefix = f"Hi {contact_name or 'there'}, this is an automated message on behalf of {user_name}."
    if not cleaned_message:
        return prefix
    punctuation = "" if cleaned_message.endswith((".", "!", "?")) else "."
    return f"{prefix} {cleaned_message}{punctuation}"


def _prepare_agent_result(schedule_request: ScheduleCallRequest, current_time_ist) -> dict:
    if schedule_request.confirmed and schedule_request.agent_result:
        agent_result = dict(schedule_request.agent_result)
    else:
        agent_result = process_message(
            raw_prompt=schedule_request.raw_prompt,
            user_name=schedule_request.user_name,
            contacts=schedule_request.contacts,
            current_time_ist=current_time_ist,
        )

    if schedule_request.confirmed_contact_name:
        agent_result["contact_name"] = schedule_request.confirmed_contact_name
    if schedule_request.confirmed_number:
        agent_result["contact_number"] = normalize_indian_number(schedule_request.confirmed_number)
    if schedule_request.confirmed_message:
        agent_result["message"] = schedule_request.confirmed_message
    if schedule_request.chosen_time:
        agent_result["scheduled_time"] = ensure_ist_datetime(schedule_request.chosen_time).isoformat()

    contact_name = str(agent_result.get("contact_name") or "").strip()
    contact_number = normalize_indian_number(str(agent_result.get("contact_number") or ""))
    message = str(agent_result.get("message") or "").strip()
    scheduled_time = None
    if agent_result.get("scheduled_time"):
        scheduled_time = ensure_ist_datetime(str(agent_result["scheduled_time"])).isoformat()

    message_preview = str(agent_result.get("rephrased_message") or "").strip()
    expected_prefix = f"Hi {contact_name or 'there'}, this is an automated message on behalf of {schedule_request.user_name}."
    if not message_preview.startswith(expected_prefix) or schedule_request.confirmed_message:
        message_preview = _build_spoken_message(schedule_request.user_name, contact_name, message)

    missing_fields = []
    if not contact_name:
        missing_fields.append("contact_name")
    if not contact_number:
        missing_fields.append("contact_number")
    if not message:
        missing_fields.append("message")
    if not scheduled_time:
        missing_fields.append("scheduled_time")

    return {
        "contact_name": contact_name,
        "contact_number": contact_number,
        "message": message,
        "rephrased_message": message_preview,
        "scheduled_time": scheduled_time,
        "time_extracted": bool(agent_result.get("time_extracted")),
        "missing_fields": missing_fields,
    }


def _agent_response(agent_result: dict, status: str, validation_message: str | None = None):
    response = {
        "status": status,
        "message_preview": agent_result["rephrased_message"],
        "missing_fields": agent_result["missing_fields"],
        "contact_name": agent_result["contact_name"],
        "contact_number": agent_result["contact_number"],
        "scheduled_time": agent_result["scheduled_time"],
        "time_extracted": agent_result["time_extracted"],
        # The browser returns this draft on confirmation so DeepSeek is not charged twice.
        "agent_result": agent_result,
    }
    if validation_message:
        response["message"] = validation_message
    return jsonify(response), 200


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

    current_time_ist = get_current_ist()
    try:
        agent_result = _prepare_agent_result(schedule_request, current_time_ist)
    except ValueError as exc:
        return jsonify({"status": "error", "message": str(exc)}), 400

    if agent_result["missing_fields"]:
        return _agent_response(agent_result, "needs_info")

    scheduled_time = ensure_ist_datetime(agent_result["scheduled_time"])
    if not is_at_least_two_minutes_ahead(scheduled_time):
        agent_result["scheduled_time"] = None
        if "scheduled_time" not in agent_result["missing_fields"]:
            agent_result["missing_fields"].append("scheduled_time")
        return _agent_response(
            agent_result,
            "needs_info",
            "Choose a time at least 2 minutes ahead of the current IST time.",
        )

    if not schedule_request.confirmed:
        return _agent_response(agent_result, "needs_confirmation")

    if not validate_indian_number(agent_result["contact_number"]):
        return jsonify({"status": "error", "message": "contact_number must be in +91XXXXXXXXXX format."}), 400

    message_preview = agent_result["rephrased_message"]

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
            contact_name=agent_result["contact_name"],
            contact_number=agent_result["contact_number"],
            original_message=agent_result["message"],
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
                "contact_name": agent_result["contact_name"],
                "contact_number": agent_result["contact_number"],
                "message_preview": message_preview,
                "audio_url": audio_url,
                "time_extracted": agent_result["time_extracted"],
                "missing_fields": [],
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
