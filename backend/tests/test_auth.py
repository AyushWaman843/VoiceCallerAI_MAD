import unittest
from datetime import timedelta
from unittest.mock import Mock, patch

import requests
from flask import Flask
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.database import Base
from app.models import AccountPlan, CallJob
from app.routes.auth import auth_bp
from app.routes.calls import calls_bp
from app.routes.payments import payments_bp
from app.utils.helpers import get_current_ist


class AuthTests(unittest.TestCase):
    def setUp(self):
        self.app = Flask(__name__)
        self.app.config.update(TESTING=True, SUPABASE_URL="https://example.supabase.co", SUPABASE_PUBLISHABLE_KEY="test")
        self.app.register_blueprint(auth_bp)
        self.app.register_blueprint(calls_bp)
        self.app.register_blueprint(payments_bp)
        self.client = self.app.test_client()
        self.engine = create_engine("sqlite://")
        Base.metadata.create_all(self.engine)
        self.session = sessionmaker(bind=self.engine)
        self.db_patch = patch("app.routes.calls.get_db", self.session)
        self.db_patch.start()
        patch("app.routes.auth.get_db", self.session).start()
        self.http = patch("app.services.auth_service.requests.Session.request").start()
        self.user = {"id": "user-a", "email": "a@example.com", "email_confirmed_at": "2026-01-01",
                     "user_metadata": {"full_name": "Account A"}}
        self.reply(self.user)
        self.headers = {"Authorization": "Bearer user-token"}
        self.app.extensions["scheduler"] = Mock()
        with self.session() as db:
            for user_id in ("user-a", "user-b"):
                db.add(CallJob(id=user_id + "-job", user_id=user_id, contact_name="Contact",
                               contact_number="+919999999999", original_message="Hello",
                               scheduled_time=get_current_ist() + timedelta(hours=1), status="pending"))
            db.commit()

    def tearDown(self):
        patch.stopall()
        self.engine.dispose()

    def reply(self, data, status=200):
        self.http.return_value = Mock(status_code=status, ok=status < 400, json=Mock(return_value=data))

    def test_protected_routes_require_bearer(self):
        for method, url in (("get", "/calls"), ("post", "/schedule-call"), ("post", "/transcribe-audio"),
                            ("delete", "/calls/user-b-job"), ("post", "/calls/user-b-job/retry"), ("get", "/auth/me")):
            with self.subTest(url=url):
                self.assertEqual(getattr(self.client, method)(url).status_code, 401)
        self.http.assert_not_called()

    def test_signup_metadata_and_confirmation(self):
        self.reply({"id": "new-user"})
        result = self.client.post("/auth/signup", json={"email": " A@example.com ", "password": "secure-password", "full_name": " Ayush "})
        self.assertEqual(result.status_code, 200)
        self.assertEqual(result.json["status"], "confirmation_required")
        self.assertNotIn("access_token", result.json)
        self.assertEqual(self.http.call_args.kwargs["json"]["data"], {"full_name": "Ayush"})
        self.assertEqual(result.headers["Cache-Control"], "no-store")

    def test_invalid_credentials_do_not_call_supabase(self):
        for payload in ([], {"email": "bad", "password": "x"},
                        {"email": "a@example.com", "password": "short", "full_name": "A"}):
            self.assertEqual(self.client.post("/auth/signup", json=payload).status_code, 400)
        self.http.assert_not_called()

    def test_disabled_email_provider_reports_exact_setting(self):
        self.reply({"error_code": "email_provider_disabled", "msg": "Email logins are disabled"}, 422)
        result = self.client.post("/auth/login", json={"email": "a@example.com", "password": "secure-password"})
        self.assertEqual(result.status_code, 400)
        self.assertIn("Enable the Email provider", result.json["message"])

    def test_login_and_refresh_return_session(self):
        self.reply({"user": self.user, "access_token": "access", "refresh_token": "refresh", "expires_in": 3600})
        result = self.client.post("/auth/login", json={"email": "a@example.com", "password": "secure-password"})
        self.assertEqual(result.json["user"]["id"], "user-a")
        result = self.client.post("/auth/refresh", json={"refresh_token": "refresh"})
        self.assertEqual(result.json["status"], "authenticated")
        self.assertIn("grant_type=refresh_token", self.http.call_args.args[1])

    def test_profile_name_requires_owner_and_persists_in_supabase(self):
        self.assertEqual(self.client.patch("/auth/profile", json={"full_name": "New Name"}).status_code, 401)
        self.assertEqual(self.client.patch("/auth/profile", headers=self.headers,
                                           json={"full_name": ""}).status_code, 400)
        self.http.side_effect = [
            Mock(status_code=200, ok=True, json=Mock(return_value=self.user)),
            Mock(status_code=200, ok=True, json=Mock(return_value={
                **self.user, "user_metadata": {"full_name": "New Name"}})),
        ]
        result = self.client.patch("/auth/profile", headers=self.headers, json={"full_name": "New Name"})
        self.assertEqual(result.json["user"]["full_name"], "New Name")
        self.assertEqual(self.http.call_args.args[:2], ("PUT", "https://example.supabase.co/auth/v1/user"))
        self.assertEqual(self.http.call_args.kwargs["json"], {"data": {"full_name": "New Name"}})

    def test_invalid_session_and_upstream_outage(self):
        self.reply({"error_code": "bad_jwt"}, 401)
        self.assertEqual(self.client.get("/calls", headers=self.headers).status_code, 401)
        self.http.side_effect = requests.Timeout()
        self.assertEqual(self.client.get("/calls", headers=self.headers).status_code, 503)

    def test_transient_account_lookup_is_retried(self):
        self.http.side_effect = [requests.Timeout(), Mock(status_code=200, ok=True,
                                                          json=Mock(return_value=self.user))]
        result = self.client.get("/calls", headers=self.headers)
        self.assertEqual(result.status_code, 200)
        self.assertEqual(self.http.call_count, 2)

    def test_list_ignores_forged_user_id(self):
        result = self.client.get("/calls?user_id=user-b", headers=self.headers)
        self.assertEqual([job["user_id"] for job in result.json["upcoming"]], ["user-a"])

    def test_cannot_cancel_or_retry_someone_elses_call(self):
        self.assertEqual(self.client.delete("/calls/user-b-job", headers=self.headers).status_code, 404)
        self.assertEqual(self.client.post("/calls/user-b-job/retry", headers=self.headers).status_code, 404)
        with self.session() as db:
            self.assertEqual(db.get(CallJob, "user-b-job").status, "pending")
        self.app.extensions["scheduler"].remove_job.assert_not_called()

    def test_schedule_uses_verified_identity_before_llm_or_tts(self):
        with self.session() as db:
            db.add(AccountPlan(user_id="user-a", plan_id="starter", credits_available=1))
            db.commit()
        with patch("app.routes.calls._prepare_agent_result", side_effect=ValueError("stop before generation")) as agent:
            self.client.post("/schedule-call", headers=self.headers, json={"user_id": "user-b", "user_name": "Fake",
                             "raw_prompt": "Call someone", "contacts": []})
        request = agent.call_args.args[0]
        self.assertEqual(request.user_id, "user-a")
        self.assertEqual(request.user_name, "Account A")

    def test_unpaid_account_cannot_spend_llm_tokens(self):
        with patch("app.routes.calls._prepare_agent_result") as agent:
            response = self.client.post("/schedule-call", headers=self.headers,
                json={"raw_prompt": "Call Friend tomorrow", "contacts": []})
        self.assertEqual(response.status_code, 403)
        agent.assert_not_called()

    def test_personal_call_reserves_credit_and_cancel_refunds(self):
        with self.session() as db:
            db.add(AccountPlan(user_id="user-a", plan_id="plus", credits_available=1))
            db.commit()
        time = (get_current_ist() + timedelta(hours=1)).isoformat()
        agent = dict(contact_name="Friend", contact_number="+919876543210", message="Hello",
                     rephrased_message="Hi Friend, hello.", scheduled_time=time,
                     time_extracted=True, missing_fields=[])
        with patch("app.routes.calls._prepare_agent_result", return_value=agent), \
             patch("app.routes.calls.generate_audio", return_value="https://example.com/media/test.mp3") as tts, \
             patch("app.routes.calls._schedule_job"):
            result = self.client.post("/schedule-call", headers=self.headers,
                                      json={"raw_prompt": "Call Friend", "contacts": [], "confirmed": True})
        self.assertEqual(result.status_code, 201)
        tts.assert_called_once()
        with self.session() as db:
            self.assertEqual(db.get(AccountPlan, "user-a").credits_available, 0)
            self.assertEqual(db.get(CallJob, result.json["job_id"]).credit_state, "reserved")
        self.assertEqual(self.client.delete(f"/calls/{result.json['job_id']}", headers=self.headers).status_code, 200)
        with self.session() as db:
            self.assertEqual(db.get(AccountPlan, "user-a").credits_available, 1)
            self.assertEqual(db.get(CallJob, result.json["job_id"]).credit_state, "refunded")

    def test_insufficient_credit_prevents_tts(self):
        with self.session() as db:
            db.add(AccountPlan(user_id="user-a", plan_id="starter", credits_available=0))
            db.commit()
        agent = dict(contact_name="Friend", contact_number="+919876543210", message="Hello",
                     rephrased_message="Hi Friend, hello.", scheduled_time=(get_current_ist() + timedelta(hours=1)).isoformat(),
                     time_extracted=True, missing_fields=[])
        with patch("app.routes.calls._prepare_agent_result", return_value=agent), \
             patch("app.routes.calls.generate_audio") as tts:
            result = self.client.post("/schedule-call", headers=self.headers,
                                      json={"raw_prompt": "Call Friend", "contacts": [], "confirmed": True})
        self.assertEqual(result.status_code, 409)
        tts.assert_not_called()

    def test_personal_audio_preview_reused_when_scheduled(self):
        with self.session() as db:
            db.add(AccountPlan(user_id="user-a", plan_id="starter", credits_available=1))
            db.commit()
        spoken = "Hi Friend, this is an automated message on behalf of Account A. Hello."
        with patch("app.routes.calls.generate_audio", return_value="https://example.com/media/test.mp3") as tts:
            preview = self.client.post("/calls/preview-audio", headers=self.headers, json={"message": spoken})
            self.assertEqual(preview.status_code, 201)
            agent = dict(contact_name="Friend", contact_number="+919876543210", message="Hello",
                         rephrased_message=spoken, scheduled_time=(get_current_ist() + timedelta(hours=1)).isoformat(),
                         time_extracted=True, missing_fields=[])
            with patch("app.routes.calls._prepare_agent_result", return_value=agent), \
                 patch("app.routes.calls._schedule_job"):
                scheduled = self.client.post("/schedule-call", headers=self.headers,
                    json={"raw_prompt": "Call Friend", "contacts": [], "confirmed": True,
                          "audio_preview_id": preview.json["preview_id"]})
            self.assertEqual(scheduled.status_code, 201)
            tts.assert_called_once_with(spoken)

    def test_logout_revokes_current_session(self):
        self.http.side_effect = [Mock(status_code=200, ok=True, json=Mock(return_value=self.user)), Mock(status_code=204)]
        self.assertEqual(self.client.post("/auth/logout", headers=self.headers).status_code, 200)
        self.assertIn("scope=local", self.http.call_args.args[1])

    def test_authenticated_user_does_not_require_email_confirmation(self):
        self.user["email_confirmed_at"] = None
        self.reply(self.user)
        self.assertEqual(self.client.get("/calls", headers=self.headers).status_code, 200)


if __name__ == "__main__":
    unittest.main()
