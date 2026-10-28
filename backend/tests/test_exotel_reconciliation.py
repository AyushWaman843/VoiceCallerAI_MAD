import unittest
from unittest.mock import patch
import requests

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.database import Base
from app.models import CallJob
from app.tasks.reconcile_calls import reconcile_calls
from app.services.exotel_service import _require_public_audio
from app.utils.helpers import get_current_ist


class ExotelReconciliationTests(unittest.TestCase):
    def test_dead_public_url_prevents_dialing(self):
        with patch("app.services.exotel_service._normalized_public_base_url", return_value="https://expired.example"), patch(
            "app.services.exotel_service.requests.get", side_effect=requests.ConnectionError("expired tunnel")
        ):
            with self.assertRaises(requests.ConnectionError):
                _require_public_audio("https://expired.example/media/message.wav")

    def test_completed_call_does_not_stay_calling(self):
        engine = create_engine("sqlite://")
        Base.metadata.create_all(engine)
        sessions = sessionmaker(bind=engine)
        with sessions() as db:
            db.add(CallJob(id="test-call", user_id="test-user", contact_name="Test",
                           contact_number="+919876543210", original_message="Hello",
                           scheduled_time=get_current_ist(), status="calling",
                           msg91_request_id="test-sid"))
            db.commit()

        with patch("app.tasks.reconcile_calls.get_db", sessions), patch(
            "app.tasks.reconcile_calls.get_call_status", return_value="completed"
        ):
            reconcile_calls()

        with sessions() as db:
            self.assertEqual(db.get(CallJob, "test-call").status, "connected")
        engine.dispose()
