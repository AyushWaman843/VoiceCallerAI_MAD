import logging
from datetime import timedelta
from time import perf_counter

from apscheduler.schedulers.background import BackgroundScheduler
from flask import Flask, g, request
from flask_cors import CORS

from .config import Config
from .database import ensure_database_schema, get_db
from .routes import calls_bp, webhooks_bp
from .routes.auth import auth_bp
from .routes.payments import payments_bp
from .routes.groups import groups_bp
from .routes.campaigns import campaigns_bp
from .routes.templates import templates_bp
from .tasks.call_task import execute_call
from .tasks.reconcile_calls import reconcile_calls
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

    @app.before_request
    def start_request_timer():
        g.request_started = perf_counter()

    @app.after_request
    def record_request_duration(response):
        elapsed_ms = (perf_counter() - g.request_started) * 1000
        response.headers["Server-Timing"] = f"app;dur={elapsed_ms:.0f}"
        if elapsed_ms >= 1000:
            app.logger.info("Slow request %s %s: %.0fms", request.method, request.path, elapsed_ms)
        return response

    from .models import CallJob

    ensure_database_schema()
    app.call_job_model = CallJob
    app.register_blueprint(calls_bp)
    app.register_blueprint(webhooks_bp)
    app.register_blueprint(auth_bp)
    app.register_blueprint(payments_bp)
    app.register_blueprint(groups_bp)
    app.register_blueprint(campaigns_bp)
    app.register_blueprint(templates_bp)

    scheduler = BackgroundScheduler(timezone=app.config["TIMEZONE"])
    scheduler.start()
    app.extensions["scheduler"] = scheduler
    _reload_pending_jobs(app, scheduler)
    scheduler.add_job(reconcile_calls, "interval", minutes=1, id="reconcile_exotel_calls", max_instances=1)
    scheduler.add_job(reconcile_calls, "date", run_date=get_current_ist() + timedelta(seconds=5),
                      id="reconcile_exotel_calls_startup")

    from .services.transcription_service import warmup_transcription
    warmup_transcription()

    return app
