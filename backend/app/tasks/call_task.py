import logging
import json

from ..database import get_db
from ..models import CallJob, Campaign, GroupContact
from ..services.plans import settle
from ..services.exotel_service import place_call
from ..services.live_audio import remember_audio, forget_audio
from ..services.tts_service import generate_audio


LOGGER = logging.getLogger(__name__)


def execute_call(job_id: str) -> None:
    db_session = get_db()
    try:
        call_job = db_session.query(CallJob).filter(CallJob.id == job_id).with_for_update().first()
        if call_job is None:
            LOGGER.error("Call job %s was not found when the scheduler tried to execute it.", job_id)
            return
        if call_job.status != "pending":
            return
        if call_job.campaign_id:
            campaign = db_session.query(Campaign).filter_by(id=call_job.campaign_id, user_id=call_job.user_id).first()
            # Recheck the exact selected memberships, including cross-group campaigns.
            approved = campaign and db_session.query(GroupContact).filter_by(
                user_id=call_job.user_id, phone=call_job.contact_number, consent_status="approved").filter(
                    GroupContact.id.in_(json.loads(campaign.contact_ids_json))).first()
            if not approved:
                call_job.status = "cancelled"
                settle(db_session, call_job, "cancelled")
                db_session.commit()
                return

        # Older rows may not have pre-generated audio yet, so we keep a compatibility fallback here.
        audio_url = call_job.audio_url or generate_audio(call_job.rephrased_message or call_job.original_message)
        if not call_job.audio_url and audio_url:
            call_job.audio_url = audio_url
        # Marking the row as calling before the API request helps audio lookup routes find the live job immediately.
        call_job.status = "calling"
        db_session.commit()
        remember_audio(call_job.id, call_job.contact_number, audio_url)
        exotel_call_sid = place_call(call_job.contact_number, audio_url)
        remember_audio(call_job.id, call_job.contact_number, audio_url, exotel_call_sid)
        # The existing column name stays for now so we do not disturb the current database schema.
        call_job.msg91_request_id = exotel_call_sid
        db_session.commit()
    except Exception:
        forget_audio(job_id)
        db_session.rollback()
        LOGGER.exception("Call execution failed for job %s.", job_id)
        failed_session = get_db()
        try:
            failed_job = failed_session.query(CallJob).filter(CallJob.id == job_id).first()
            if failed_job is not None:
                failed_job.status = "failed"
                settle(failed_session, failed_job, "failed")
                failed_session.commit()
        except Exception:
            failed_session.rollback()
            LOGGER.exception("Failed to mark call job %s as failed after execution error.", job_id)
        finally:
            failed_session.close()
    finally:
        db_session.close()
