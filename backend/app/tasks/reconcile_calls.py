import logging

from ..database import get_db
from ..models import CallJob
from ..services.exotel_service import get_call_status, map_exotel_status
from ..services.plans import settle


LOGGER = logging.getLogger(__name__)


def reconcile_calls() -> None:
    db = get_db()
    try:
        jobs = (db.query(CallJob).filter(CallJob.status == "calling", CallJob.msg91_request_id.isnot(None))
                .order_by(CallJob.updated_at).limit(10).all())
        for job in jobs:
            try:
                provider_status = get_call_status(job.msg91_request_id)
                if provider_status not in {"completed", "busy", "no-answer", "failed", "canceled", "cancelled", "rejected"}:
                    continue
                db.refresh(job)
                if job.status != "calling":
                    continue
                outcome = map_exotel_status(provider_status)
                job.status = outcome
                settle(db, job, outcome)
                db.commit()
                LOGGER.info("Reconciled Exotel call %s as %s", job.id, outcome)
            except Exception:
                db.rollback()
                LOGGER.exception("Could not reconcile Exotel call %s", job.id)
    finally:
        db.close()
