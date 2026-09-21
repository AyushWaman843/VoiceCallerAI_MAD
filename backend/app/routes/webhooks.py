from urllib.parse import quote, unquote, urlparse

from flask import Blueprint, Response, current_app, jsonify, request

from ..database import get_db
from ..models import CallJob
from ..config import Config


webhooks_bp = Blueprint("webhooks", __name__)


def _extract_exotel_call_sid(payload: dict):
    return payload.get("CallSid") or payload.get("callsid") or payload.get("Sid") or payload.get("sid")


def _map_exotel_status(payload: dict) -> str:
    event_type = str(payload.get("EventType") or payload.get("event_type") or "").strip().lower()
    raw_status = str(
        payload.get("Status")
        or payload.get("status")
        or payload.get("CallStatus")
        or payload.get("call_status")
        or payload.get("DialCallStatus")
        or payload.get("dial_call_status")
        or ""
    ).strip().lower()
    if event_type == "answered":
        return "connected"
    if raw_status in {"completed", "answered", "in-progress", "in progress"}:
        return "connected"
    if raw_status in {"failed", "busy", "no-answer", "no answer", "canceled", "cancelled", "rejected"}:
        return "failed"
    return ""


def _normalize_phone_suffix(value: str) -> str:
    digits_only = "".join(character for character in str(value or "") if character.isdigit())
    return digits_only[-10:] if len(digits_only) >= 10 else digits_only


def _canonicalize_audio_url(audio_url: str) -> str:
    normalized_audio_url = str(audio_url or "").strip()
    if not normalized_audio_url:
        return ""

    public_base_url = str(Config.PUBLIC_BASE_URL or "").strip().rstrip("/")
    if not public_base_url:
        return normalized_audio_url

    parsed_audio_url = urlparse(normalized_audio_url)
    if "/media/" not in parsed_audio_url.path:
        return normalized_audio_url

    filename = unquote(parsed_audio_url.path.rsplit("/", 1)[-1]).strip()
    if not filename:
        return normalized_audio_url

    # Rebuilding the public media URL keeps old rows playable after the Cloudflare tunnel changes.
    return f"{public_base_url}/media/{quote(filename)}"


def _resolve_audio_url_from_request() -> str:
    explicit_audio_url = str(request.args.get("audio_url", "")).strip()
    if explicit_audio_url:
        return _canonicalize_audio_url(explicit_audio_url)

    call_sid = _extract_exotel_call_sid(request.args.to_dict())
    job_id = str(request.args.get("job_id", "")).strip()
    incoming_number_suffix = _normalize_phone_suffix(
        request.args.get("CallFrom")
        or request.args.get("From")
        or request.args.get("contact_number")
        or request.args.get("phone")
        or ""
    )

    db_session = get_db()
    try:
        call_job = None
        if call_sid:
            call_job = db_session.query(CallJob).filter(CallJob.msg91_request_id == call_sid).first()
        if call_job is None and job_id:
            call_job = db_session.query(CallJob).filter(CallJob.id == job_id).first()
        if call_job is None and incoming_number_suffix:
            candidate_jobs = (
                db_session.query(CallJob)
                .filter(CallJob.status.in_(["calling", "connected", "pending"]))
                .order_by(CallJob.updated_at.desc())
                .all()
            )
            call_job = next(
                (
                    job
                    for job in candidate_jobs
                    if _normalize_phone_suffix(job.contact_number) == incoming_number_suffix and job.audio_url
                ),
                None,
            )
        if call_job is None:
            call_job = (
                db_session.query(CallJob)
                .filter(CallJob.status.in_(["calling", "connected"]))
                .filter(CallJob.audio_url.isnot(None))
                .order_by(CallJob.updated_at.desc())
                .first()
            )
        return _canonicalize_audio_url(call_job.audio_url) if call_job and call_job.audio_url else ""
    finally:
        db_session.close()


@webhooks_bp.get("/exotel/play-audio")
def exotel_play_audio():
    audio_url = _resolve_audio_url_from_request()
    current_app.logger.info("Exotel requested play instructions with args=%s, resolved_audio_url=%s", request.args.to_dict(), audio_url)
    if not audio_url:
        return jsonify({"status": "error", "message": "Unable to resolve an audio URL for this Exotel request."}), 404

    # Exotel fetches this XML when the callee answers, so the URL must stay publicly reachable.
    response_xml = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        "<Response>\n"
        f"  <Play>{audio_url}</Play>\n"
        "  <Hangup/>\n"
        "</Response>"
    )
    response = Response(response_xml, status=200, mimetype="application/xml")
    response.headers["Cache-Control"] = "no-store"
    return response


@webhooks_bp.get("/exotel/audio-url")
@webhooks_bp.get("/exotel-audio-url")
def exotel_audio_url():
    audio_url = _resolve_audio_url_from_request()
    current_app.logger.info("Exotel requested direct audio URL with args=%s, resolved_audio_url=%s", request.args.to_dict(), audio_url)
    if not audio_url:
        return jsonify({"status": "error", "message": "Unable to resolve an audio URL for this Exotel request."}), 404
    response = Response(audio_url, status=200, mimetype="text/plain")
    response.headers["Cache-Control"] = "no-store"
    return response


@webhooks_bp.route("/webhook/exotel", methods=["GET", "POST"])
def exotel_webhook():
    payload = request.values.to_dict() or {}
    json_payload = request.get_json(silent=True) or {}
    payload.update(json_payload)
    current_app.logger.info("Received Exotel webhook via %s with payload=%s", request.method, payload)

    if request.method == "GET" and not payload:
        # The current Exotel flow screenshot points the Greeting applet at this callback URL, so we provide
        # a plain-text audio URL fallback here to avoid wasting another TTS generation while the dashboard is updated.
        audio_url = _resolve_audio_url_from_request()
        current_app.logger.info("Exotel webhook fallback resolved audio URL %s for an empty GET request.", audio_url)
        if not audio_url:
            return jsonify({"status": "error", "message": "Unable to resolve an audio URL for this Exotel request."}), 404
        response = Response(audio_url, status=200, mimetype="text/plain")
        response.headers["Cache-Control"] = "no-store"
        return response

    call_sid = _extract_exotel_call_sid(payload)
    mapped_status = _map_exotel_status(payload)

    if not call_sid:
        return jsonify({"status": "ignored", "message": "CallSid not provided."}), 200

    if not mapped_status:
        return jsonify({"status": "ignored", "message": "No supported Exotel status found."}), 200

    db_session = get_db()
    try:
        call_job = db_session.query(CallJob).filter(CallJob.msg91_request_id == call_sid).first()
        if call_job is None:
            return jsonify({"status": "ignored", "message": "Matching call job not found."}), 200

        call_job.status = mapped_status
        db_session.commit()
    except Exception:
        db_session.rollback()
        return jsonify({"status": "error", "message": "Failed to update Exotel call status."}), 500
    finally:
        db_session.close()

    return jsonify({"status": "ok"}), 200


@webhooks_bp.post("/webhook/msg91")
def msg91_webhook():
    # This endpoint is kept as a stub so any old integrations fail softly after the Exotel migration.
    return jsonify({"status": "deprecated", "message": "MSG91 webhook is no longer used. Use /webhook/exotel."}), 200
