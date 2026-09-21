import logging
from datetime import timedelta

from apscheduler.schedulers.background import BackgroundScheduler
from flask import Flask
from flask_cors import CORS

from .config import Config
from .database import ensure_database_schema, get_db
from .routes import calls_bp, webhooks_bp
from .tasks.call_task import execute_call
from .utils.helpers import get_current_ist


def _schedule_job(scheduler: BackgroundScheduler, job_id: str, scheduled_time) -> None:
    # Overdue pending jobs are scheduled a few seconds ahead so they are retried after a restart.
    run_at = scheduled_time if scheduled_time >= get_current_ist() else get_current_ist() + timedelta(seconds=5)
    scheduler.add_job(
        func=execute_call,
        trigger="date",
        run_date=run_at,
        args=[job_id],
        id=job_id,
        replace_existing=True,
        misfire_grace_time=3600,
    )


def _reload_pending_jobs(app: Flask, scheduler: BackgroundScheduler) -> None:
    db_session = get_db()
    try:
        pending_jobs = (
            db_session.query(app.call_job_model)
            .filter(app.call_job_model.status == "pending")
            .all()
        )
        for pending_job in pending_jobs:
            _schedule_job(scheduler, pending_job.id, pending_job.scheduled_time)
    except Exception:
        app.logger.exception("Failed to reload pending call jobs into APScheduler.")
    finally:
        db_session.close()


def create_app() -> Flask:
    app = Flask(__name__)
    app.config.from_object(Config)
    CORS(app)

    # Central logging keeps background task failures visible during local development.
    logging.basicConfig(level=logging.INFO)

    from .models import CallJob

    ensure_database_schema()
    app.call_job_model = CallJob
    app.register_blueprint(calls_bp)
    app.register_blueprint(webhooks_bp)

    scheduler = BackgroundScheduler(timezone=app.config["TIMEZONE"])
    scheduler.start()
    app.extensions["scheduler"] = scheduler
    _reload_pending_jobs(app, scheduler)

    return app
