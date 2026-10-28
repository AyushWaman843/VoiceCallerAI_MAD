import io
import json
import unittest
from datetime import timedelta
from unittest.mock import patch

from flask import Flask
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.database import Base
from app.models import AccountPlan, CallJob, ContactGroup, CreditEvent, GroupContact
from app.services.plans import DEFAULT_PLANS
from app.routes.auth import auth_bp
from app.routes.payments import payments_bp
from app.routes.groups import groups_bp
from app.routes.campaigns import campaigns_bp
from app.routes.webhooks import webhooks_bp
from app.tasks.call_task import execute_call
from app.utils.helpers import get_current_ist


class BusinessTests(unittest.TestCase):
    def setUp(self):
        self.app = Flask(__name__)
        self.app.config.update(TESTING=True, EXOTEL_ESTIMATED_COST_PAISE=100)
        for bp in (auth_bp, payments_bp, groups_bp, campaigns_bp, webhooks_bp):
            self.app.register_blueprint(bp)
        self.client = self.app.test_client()
        self.engine = create_engine("sqlite://")
        Base.metadata.create_all(self.engine)
        self.sessions = sessionmaker(bind=self.engine)
        for module in ("app.routes.payments", "app.routes.groups", "app.routes.campaigns", "app.routes.webhooks"):
            patch(f"{module}.get_db", self.sessions).start()
        self.user = patch("app.services.auth_service.supabase_request", return_value={
            "id": "business-a", "email": "a@example.com", "user_metadata": {"full_name": "Owner"}}).start()
        self.tts = patch("app.routes.campaigns.generate_audio", return_value="https://public.test/audio.mp3").start()
        self.schedule = patch("app.routes.campaigns._schedule_job").start()
        self.headers = {"Authorization": "Bearer test"}
        with self.sessions() as db:
            db.add(AccountPlan(user_id="business-a", plan_id="business", credits_available=10))
            db.add(AccountPlan(user_id="personal-b", plan_id="plus", credits_available=10))
            db.commit()

    def tearDown(self):
        patch.stopall()
        self.engine.dispose()

    def post(self, path, body):
        return self.client.post(path, json=body, headers=self.headers)

    def group(self):
        response = self.post("/groups", {"name": "Batch B Parents"})
        self.assertEqual(response.status_code, 201)
        return response.json["group"]["id"]

    def test_business_gate_and_account_isolation(self):
        group_id = self.group()
        self.user.return_value["id"] = "personal-b"
        self.assertEqual(self.client.get("/groups", headers=self.headers).status_code, 403)
        self.user.return_value["id"] = "business-c"
        with self.sessions() as db:
            db.add(AccountPlan(user_id="business-c", plan_id="business", credits_available=10))
            db.commit()
        self.assertEqual(self.client.get("/groups", headers=self.headers).json["groups"], [])
        self.assertEqual(self.post(f"/groups/{group_id}/contacts", {"name": "X", "number": "9876543210"}).status_code, 404)

    def test_catalog_entitlement_is_enforced_by_server(self):
        configured = {key: dict(value) for key, value in DEFAULT_PLANS.items()}
        configured["business"]["groups"] = False
        self.app.config["PLAN_CATALOG_JSON"] = json.dumps(configured)
        self.assertEqual(self.post("/groups", {"name": "Blocked"}).status_code, 403)

    def test_manual_contact_consent_and_optout(self):
        group_id = self.group()
        created = self.post(f"/groups/{group_id}/contacts", {"name": "Parent", "number": "9876543210"})
        self.assertEqual(created.json["contact"]["consent_status"], "pending")
        contact_id = created.json["contact"]["id"]
        result = self.client.patch(f"/groups/{group_id}/contacts/{contact_id}",
            json={"consent_confirmed": True}, headers=self.headers)
        self.assertEqual(result.json["contact"]["consent_status"], "approved")
        result = self.client.patch(f"/groups/{group_id}/contacts/{contact_id}",
            json={"opt_out": True}, headers=self.headers)
        self.assertEqual(result.json["contact"]["consent_status"], "opted_out")

    def test_csv_review_requires_approval_and_corrects_bad_rows(self):
        file = b"Parent Name,Mobile No,Unrelated\nAlice,9876543210,a\nBob,9876543210,b\nCara,bad,c\n"
        result = self.client.post("/groups/imports/preview", data={"group_name": "Batch B Parents",
            "file": (io.BytesIO(file), "contacts.csv")}, headers=self.headers)
        self.assertEqual(result.status_code, 200)
        self.assertEqual((result.json["found"], result.json["duplicates"], result.json["invalid"]), (3, 1, 1))
        with self.sessions() as db:
            self.assertEqual(db.query(GroupContact).count(), 0)
        batch = result.json["import_id"]
        approved = self.post(f"/groups/imports/{batch}/approve", {"consent_confirmed": True,
            "contacts": [{"row": 0}, {"row": 2, "number": "9123456789"}]})
        self.assertEqual(approved.status_code, 201)
        self.assertEqual(approved.json["callable"], 2)
        self.assertEqual(self.post(f"/groups/imports/{batch}/approve", {"contacts": [{"row": 0}]}).status_code, 404)

    def test_ambiguous_columns_request_selection(self):
        result = self.client.post("/groups/imports/preview", data={"group_name": "A",
            "file": (io.BytesIO(b"Name,Full Name,Phone\nA,Alice,9876543210\n"), "contacts.csv")}, headers=self.headers)
        self.assertEqual(result.json["status"], "needs_columns")

    def test_headerless_two_column_csv_keeps_first_contact(self):
        result = self.client.post("/groups/imports/preview", data={"group_name": "Simple",
            "file": (io.BytesIO(b"Alice,9876543210\nBob,9123456789\n"), "simple.csv")}, headers=self.headers)
        self.assertEqual(result.status_code, 200)
        self.assertEqual(result.json["found"], 2)

    def test_campaign_one_job_per_contact_and_credit_refund(self):
        group_id = self.group()
        for number in ("9876543210", "9123456789"):
            self.post(f"/groups/{group_id}/contacts", {"name": "Parent", "number": number,
                "consent_confirmed": True})
        scheduled = (get_current_ist() + timedelta(hours=1)).isoformat()
        preview = self.post("/campaigns/preview", {"group_id": group_id,
            "message": "Reminder about Monday's exam", "scheduled_time": scheduled})
        self.assertEqual(preview.status_code, 200)
        campaign = preview.json["campaign"]
        self.assertEqual(campaign["estimated_credits"], 2)
        self.assertEqual(campaign["estimated_provider_cost_paise"], 200)
        self.assertEqual(self.post(f"/campaigns/{campaign['id']}/schedule", {}).status_code, 400)
        result = self.post(f"/campaigns/{campaign['id']}/schedule", {"confirmed": True})
        self.assertEqual(result.status_code, 201)
        self.assertEqual(result.json["jobs"], 2)
        self.assertEqual(self.post(f"/campaigns/{campaign['id']}/schedule", {"confirmed": True}).status_code, 409)
        with self.sessions() as db:
            self.assertEqual(db.get(AccountPlan, "business-a").credits_available, 8)
            self.assertEqual(db.query(CallJob).count(), 2)
        self.assertEqual(self.post(f"/campaigns/{campaign['id']}/cancel", {}).json["refunded"], 2)
        with self.sessions() as db:
            self.assertEqual(db.get(AccountPlan, "business-a").credits_available, 10)
            self.assertEqual(db.query(CreditEvent).filter_by(source_type="refund").count(), 2)

    def test_optout_before_execution_prevents_call_and_refunds(self):
        group_id = self.group()
        added = self.post(f"/groups/{group_id}/contacts", {"name": "Parent", "number": "9876543210",
            "consent_confirmed": True}).json["contact"]
        preview = self.post("/campaigns/preview", {"group_id": group_id, "message": "Reminder",
            "scheduled_time": (get_current_ist() + timedelta(hours=1)).isoformat()}).json["campaign"]
        self.post(f"/campaigns/{preview['id']}/schedule", {"confirmed": True})
        self.client.patch(f"/groups/{group_id}/contacts/{added['id']}",
            json={"opt_out": True}, headers=self.headers)
        with self.sessions() as db:
            job_id = db.query(CallJob).filter_by(campaign_id=preview["id"]).first().id
        with patch("app.tasks.call_task.get_db", self.sessions), patch("app.tasks.call_task.place_call") as call:
            execute_call(job_id)
        call.assert_not_called()
        with self.sessions() as db:
            self.assertEqual(db.get(CallJob, job_id).status, "cancelled")
            self.assertEqual(db.get(AccountPlan, "business-a").credits_available, 10)

    def test_no_answer_callback_marks_missed_and_refunds_once(self):
        group_id = self.group()
        self.post(f"/groups/{group_id}/contacts", {"name": "Parent", "number": "9876543210",
            "consent_confirmed": True})
        campaign = self.post("/campaigns/preview", {"group_id": group_id, "message": "Reminder",
            "scheduled_time": (get_current_ist() + timedelta(hours=1)).isoformat()}).json["campaign"]
        self.post(f"/campaigns/{campaign['id']}/schedule", {"confirmed": True})
        with self.sessions() as db:
            job = db.query(CallJob).filter_by(campaign_id=campaign["id"]).first()
            job.status = "calling"
            job.msg91_request_id = "exotel-test-sid"
            db.commit()
        for attempt in range(2):
            response = self.client.post("/webhook/exotel", data={"CallSid": "exotel-test-sid", "Status": "no-answer"})
            self.assertEqual(response.status_code, 200, response.json)
            if attempt == 0:
                self.assertEqual(response.json["status"], "ok", response.json)
        with self.sessions() as db:
            self.assertEqual(db.query(CallJob).filter_by(campaign_id=campaign["id"]).first().status, "missed")
            self.assertEqual(db.get(AccountPlan, "business-a").credits_available, 10)
            self.assertEqual(db.query(CreditEvent).filter_by(source_type="refund").count(), 1)


if __name__ == "__main__":
    unittest.main()
