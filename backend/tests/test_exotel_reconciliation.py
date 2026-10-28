import unittest
from unittest.mock import patch
import requests

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.database import Base
from app.models import CallJob, Campaign
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

    def test_campaign_finishes_when_last_call_finishes(self):
        engine = create_engine("sqlite://")
        Base.metadata.create_all(engine)
        sessions = sessionmaker(bind=engine, autoflush=False)
        with sessions() as db:
            db.add(Campaign(id="campaign-1", user_id="test-user", group_id="group-1",
                            title="Reminder", message="Hello", audio_url="https://example.test/audio.wav",
                            scheduled_time=get_current_ist(), timezone="Asia/Kolkata",
                            contact_ids_json="[]", interval_json="{}", status="scheduled"))
            for index, status in enumerate(("connected", "failed", "calling")):
                db.add(CallJob(id=f"call-{index}", user_id="test-user", campaign_id="campaign-1",
                               contact_name="Test", contact_number="+919876543210", original_message="Hello",
                               scheduled_time=get_current_ist(), status=status,
                               msg91_request_id="last-sid" if status == "calling" else None))
            db.commit()

        with patch("app.tasks.reconcile_calls.get_db", sessions), patch(
            "app.tasks.reconcile_calls.get_call_status", return_value="completed"
        ):
            reconcile_calls()

        with sessions() as db:
            self.assertEqual(db.get(Campaign, "campaign-1").status, "finished")
            self.assertEqual(db.get(CallJob, "call-2").status, "connected")
        engine.dispose()
