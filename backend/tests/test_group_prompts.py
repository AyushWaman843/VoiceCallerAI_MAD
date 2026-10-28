import unittest
from datetime import datetime, timedelta
from unittest.mock import patch
from zoneinfo import ZoneInfo

import test_business
from app.models import CallJob, MessageTemplate
from app.routes.templates import templates_bp, resolve_template
from app.routes.calls import ScheduleCallRequest, _prepare_agent_result
from app.routes.payments import PaymentError
from app.services.campaign_timing import interval_from_instruction, recipient_time
from app.services.group_recipients import SELECTED_GROUPS
from app.utils.helpers import get_current_ist


class GroupPromptTests(unittest.TestCase):
    post = test_business.BusinessTests.post
    group = test_business.BusinessTests.group
    tearDown = test_business.BusinessTests.tearDown

    def setUp(self):
        test_business.BusinessTests.setUp(self)
        self.app.register_blueprint(templates_bp)
        patch("app.routes.templates.get_db", self.sessions).start()
        patch("app.routes.calls.get_db", self.sessions).start()
        self.first = (get_current_ist() + timedelta(hours=1)).replace(microsecond=0)
        self.agent = patch("app.routes.campaigns.process_message", return_value={
            "message": "Exam tomorrow", "scheduled_time": self.first.isoformat(), "interval": None}).start()

    def contacts(self):
        group = self.group()
        ids = [self.post(f"/groups/{group}/contacts", {"name": name, "number": number,
            "consent_confirmed": True}).json["contact"]["id"]
            for name, number in [("First", "9876543210"), ("Excluded", "9123456789"), ("Last", "9988776655")]]
        return group, ids

    def test_selected_ids_order_and_stagger_are_preserved_until_schedule(self):
        group, ids = self.contacts()
        result = self.post("/campaigns/preview", {"group_id": group, "recipient_ids": [ids[2], ids[0]],
            "raw_prompt": "Tomorrow at 3pm say exam tomorrow, call each person every five minutes"})
        self.assertEqual(result.status_code, 200, result.json)
        campaign = result.json["campaign"]
        self.assertEqual([r["id"] for r in campaign["recipients"]], [ids[2], ids[0]])
        times = [datetime.fromisoformat(r["scheduled_time"]) for r in campaign["recipients"]]
        self.assertEqual(times, [self.first, self.first + timedelta(minutes=5)])
        result = self.post(f"/campaigns/{campaign['id']}/schedule", {"confirmed": True})
        self.assertEqual(result.status_code, 201)
        with self.sessions() as db:
            jobs = db.query(CallJob).order_by(CallJob.scheduled_time).all()
            self.assertEqual([j.contact_name for j in jobs], ["Last", "First"])
            self.assertEqual(jobs[1].scheduled_time - jobs[0].scheduled_time, timedelta(minutes=5))
        self.tts.assert_called_once()

    def test_empty_or_foreign_selection_never_falls_back_to_whole_group(self):
        group, ids = self.contacts()
        other_group = self.post("/groups", {"name": "Other"}).json["group"]["id"]
        other = self.post(f"/groups/{other_group}/contacts", {"name": "Other", "number": "9765432109", "consent_confirmed": True}).json["contact"]["id"]
        for selected in ([], [other], [ids[0], "missing"]):
            result = self.post("/campaigns/preview", {"group_id": group, "recipient_ids": selected, "raw_prompt": "Call everyone tomorrow"})
            self.assertEqual(result.status_code, 409)
        self.tts.assert_not_called()

    def test_interval_without_start_requests_clarification_without_tts(self):
        group, ids = self.contacts()
        self.agent.return_value["scheduled_time"] = None
        response = self.post("/campaigns/preview", {"group_id": group, "recipient_ids": ids, "raw_prompt": "Say hello every 5 minutes"})
        self.assertEqual(response.status_code, 400)
        self.tts.assert_not_called()

    def test_template_crud_unique_names_and_account_isolation(self):
        response = self.post("/templates", {"name": "Exam reminder", "message": "Please bring your books."})
        self.assertEqual(response.status_code, 201)
        key = response.json["template"]["id"]
        self.assertEqual(self.post("/templates", {"name": "EXAM REMINDER", "message": "Other"}).status_code, 409)
        self.assertEqual(self.client.patch(f"/templates/{key}", json={"name": "Exam", "message": "Bring pens."}, headers=self.headers).status_code, 200)
        self.user.return_value["id"] = "personal-b"
        self.assertEqual(self.client.get("/templates", headers=self.headers).json["templates"], [])
        self.assertEqual(self.client.delete(f"/templates/{key}", headers=self.headers).status_code, 404)
        self.user.return_value["id"] = "business-a"
        self.assertEqual(self.client.delete(f"/templates/{key}", headers=self.headers).status_code, 200)

    def test_saved_template_is_spoken_exactly_and_not_used_for_time_extraction(self):
        self.post("/templates", {"name": "Exam", "message": "The exam is every Monday at 10am."})
        group, ids = self.contacts()
        result = self.post("/campaigns/preview", {"group_id": group, "recipient_ids": ids[:1],
            "raw_prompt": 'Use "Exam" template tomorrow at 3pm'})
        self.assertEqual(result.status_code, 200, result.json)
        self.assertTrue(result.json["campaign"]["message"].endswith("The exam is every Monday at 10am."))
        self.assertNotIn("Monday", self.agent.call_args.args[0])
        self.assertEqual(result.json["campaign"]["interval"], {})

    def test_personal_template_reuses_existing_recipient_and_time_flow(self):
        self.post("/templates", {"name": "Hello", "message": "How are you?"})
        with self.app.test_request_context(), patch("app.routes.calls.process_message", return_value={
            "contact_name": "Mom", "contact_number": "+919876543210", "message": "wrong", "scheduled_time": self.first.isoformat(), "time_extracted": True}):
            result = _prepare_agent_result(ScheduleCallRequest("business-a", "Owner", 'Call Mom use "Hello" template tomorrow', []), get_current_ist())
        self.assertEqual(result["message"], "How are you?")
        self.assertTrue(result["rephrased_message"].endswith("How are you?"))
        self.assertEqual(result["missing_fields"], [])

    def test_missing_template_fails_before_model_or_audio(self):
        group, ids = self.contacts()
        response = self.post("/campaigns/preview", {"group_id": group, "recipient_ids": ids, "raw_prompt": 'Use "Missing" template tomorrow'})
        self.assertEqual(response.status_code, 404)
        self.agent.assert_not_called()
        self.tts.assert_not_called()

    def test_schedule_only_edits_reuse_audio(self):
        group, ids = self.contacts()
        for prompt in ("Say exam tomorrow at 3pm", "Say exam tomorrow at 4pm every 2 hours"):
            result = self.post("/campaigns/preview", {"group_id": group, "recipient_ids": ids, "raw_prompt": prompt})
            self.assertEqual(result.status_code, 200, result.json)
        self.tts.assert_called_once()

    def named_groups(self):
        appointments = self.post("/groups", {"name": "Appointments"}).json["group"]["id"]
        customers = self.post("/groups", {"name": "Customers"}).json["group"]["id"]
        ids = []
        for group, name, number in [(appointments, "Aarav Sharma", "9876543210"),
                                    (appointments, "Riya Shah", "9123456789"),
                                    (customers, "Neha Singh", "9988776655")]:
            ids.append(self.post(f"/groups/{group}/contacts", {"name": name, "number": number,
                "consent_confirmed": True}).json["contact"]["id"])
        return appointments, customers, ids

    def test_named_person_from_group_using_the_template(self):
        _, _, ids = self.named_groups()
        self.post("/templates", {"name": "Reminder", "message": "Your appointment is tomorrow."})
        prompt = "call aarav from appointments group using the reminder template at 9:15"
        result = self.post("/campaigns/resolve", {"raw_prompt": prompt})
        self.assertEqual(result.status_code, 200, result.json)
        contacts = result.json["selection"]["contacts"]
        self.assertEqual([c["id"] for c in contacts], ids[:1])
        self.assertEqual(contacts[0]["group_name"], "Appointments")
        preview = self.post("/campaigns/preview", {"group_id": SELECTED_GROUPS, "recipient_ids": ids[:1], "raw_prompt": prompt})
        self.assertEqual(preview.status_code, 200, preview.json)
        self.assertTrue(preview.json["campaign"]["message"].endswith("Your appointment is tomorrow."))

    def test_screenshot_prompt_resolves_only_aarav_and_reminder(self):
        _, _, ids = self.named_groups()
        self.post("/templates", {"name": "Reminder", "message": "Please attend your appointment."})
        prompt = "Call aarav from the appointments group and use the template reminder for sending the message at 6.03 pm today."
        response = self.post("/campaigns/resolve", {"raw_prompt": prompt})
        self.assertEqual(response.status_code, 200, response.json)
        self.assertEqual([c["id"] for c in response.json["selection"]["contacts"]], ids[:1])
        preview = self.post("/campaigns/preview", {"group_id": SELECTED_GROUPS,
            "recipient_ids": ids[:1], "raw_prompt": prompt})
        self.assertEqual(preview.status_code, 200, preview.json)
        self.assertTrue(preview.json["campaign"]["message"].endswith("Please attend your appointment."))
        self.assertIn("6.03 pm today", self.agent.call_args.args[0])

    def test_template_before_name_uses_longest_saved_name_and_checks_owner(self):
        for name in ("Reminder", "Reminder Follow Up"):
            self.post("/templates", {"name": name, "message": name + " text"})
        with self.sessions() as db:
            instruction, message = resolve_template(db, "business-a", "use the template reminder follow up at 9:15")
            self.assertEqual(message, "Reminder Follow Up text")
            self.assertIn("at 9:15", instruction)
            with self.assertRaises(PaymentError):
                resolve_template(db, "other-user", "use the template reminder at 9:15")
            with self.assertRaises(PaymentError):
                resolve_template(db, "business-a", "use the template nonexistent at 9:15")

    def test_transcription_hints_are_private_and_bounded(self):
        from app.services.transcription_service import transcription_hints
        self.named_groups()
        self.post("/templates", {"name": "Reminder", "message": "Not a vocabulary hint"})
        with patch("app.database.get_db", self.sessions):
            hints = transcription_hints("business-a")
            self.assertIn("Aarav Sharma", hints)
            self.assertIn("Appointments", hints)
            self.assertIn("Reminder", hints)
            self.assertNotIn("Not a vocabulary hint", hints)
            self.assertLessEqual(len(hints), 500)
            self.assertIsNone(transcription_hints("other-user"))

    def test_saved_remider_matches_reminder_with_one_final_confirmation(self):
        _, _, ids = self.named_groups()
        self.post("/templates", {"name": "Remider", "message": "Please attend your appointment."})
        for phrase in ("use the template reminder to send a message", "use the reminder template"):
            prompt = f"Call Aarav from Appointments group and {phrase} at 6.15pm today"
            result = self.post("/campaigns/preview", {"group_id": SELECTED_GROUPS,
                "recipient_ids": ids[:1], "raw_prompt": prompt})
            self.assertEqual(result.status_code, 200, result.json)
            self.assertEqual(result.json["status"], "needs_confirmation")
            self.assertEqual(result.json["campaign"]["template_name"], "Remider")
            self.assertTrue(result.json["campaign"]["message"].endswith("Please attend your appointment."))
        with self.sessions() as db:
            self.assertEqual(db.query(CallJob).count(), 0)
            with self.assertRaises(PaymentError):
                resolve_template(db, "other-account", "use the template reminder tomorrow")

    def test_template_typo_never_guesses_between_similar_names(self):
        for name in ("Remider", "Remindr"):
            self.post("/templates", {"name": name, "message": name})
        with self.sessions() as db:
            with self.assertRaisesRegex(PaymentError, "ambiguous"):
                resolve_template(db, "business-a", "use the template reminder at 9:15")
            _, message = resolve_template(db, "business-a", "use the Remider template tomorrow")
            self.assertEqual(message, "Remider")

    def test_multiple_people_and_groups_only_schedule_selected_people(self):
        appointments, customers, ids = self.named_groups()
        response = self.post("/campaigns/resolve", {"raw_prompt": "Call Aarav and Riya from Appointments group and Neha from Customers group tomorrow at 3pm"})
        self.assertEqual(response.status_code, 200, response.json)
        self.assertEqual([c["id"] for c in response.json["selection"]["contacts"]], ids)
        # Deselect Riya; only the other two jobs can ever be created.
        preview = self.post("/campaigns/preview", {"group_id": SELECTED_GROUPS, "recipient_ids": [ids[0], ids[2]], "raw_prompt": "Tomorrow at 3pm say hello"}).json["campaign"]
        self.assertEqual(self.post(f"/campaigns/{preview['id']}/schedule", {"confirmed": True}).status_code, 201)
        with self.sessions() as db:
            jobs = db.query(CallJob).filter_by(campaign_id=preview["id"]).all()
            self.assertEqual({j.contact_name for j in jobs}, {"Aarav Sharma", "Neha Singh"})
        self.assertEqual(self.client.delete(f"/groups/{customers}", headers=self.headers).status_code, 409)
        with patch("app.tasks.call_task.get_db", self.sessions), patch("app.tasks.call_task.place_call", return_value="test-sid") as place:
            from app.tasks.call_task import execute_call
            execute_call(jobs[0].id)
        place.assert_called_once()

    def test_call_to_both_people_uses_exact_two_group_members_and_saved_template(self):
        appointments, _, ids = self.named_groups()
        akshay = self.post(f"/groups/{appointments}/contacts", {"name": "Akshay Damle",
            "number": "9920349820", "consent_confirmed": True}).json["contact"]["id"]
        self.post("/templates", {"name": "Reminder", "message": "Please attend your appointment."})
        prompt = ("Place a call to both Aarav and Akshay Damle from appointments group "
                  "using the template reminder and send them a message at 9.30 pm")
        resolved = self.post("/campaigns/resolve", {"raw_prompt": prompt})
        self.assertEqual(resolved.status_code, 200, resolved.json)
        chosen = resolved.json["selection"]["contacts"]
        self.assertEqual([contact["id"] for contact in chosen], [ids[0], akshay])
        preview = self.post("/campaigns/preview", {"group_id": SELECTED_GROUPS,
            "recipient_ids": [contact["id"] for contact in chosen], "raw_prompt": prompt})
        self.assertEqual(preview.status_code, 200, preview.json)
        self.assertEqual([recipient["id"] for recipient in preview.json["campaign"]["recipients"]], [ids[0], akshay])
        self.assertEqual(preview.json["campaign"]["template_name"], "Reminder")
        self.assertIn("Please attend your appointment.", preview.json["campaign"]["message"])
        self.assertIn("9.30 pm", self.agent.call_args.args[0])
        campaign_id = preview.json["campaign"]["id"]
        scheduled = self.post(f"/campaigns/{campaign_id}/schedule", {"confirmed": True})
        self.assertEqual(scheduled.status_code, 201, scheduled.json)
        self.assertEqual(scheduled.json["jobs"], 2)
        with self.sessions() as db:
            jobs = db.query(CallJob).filter_by(campaign_id=campaign_id).all()
            self.assertEqual({job.contact_name for job in jobs}, {"Aarav Sharma", "Akshay Damle"})

    def test_alternate_two_person_phrasings_select_only_named_contacts(self):
        appointments, _, ids = self.named_groups()
        akshay = self.post(f"/groups/{appointments}/contacts", {"name": "Akshay Damle",
            "number": "9920349820", "consent_confirmed": True}).json["contact"]["id"]
        for prompt in (
            "Call Aarav and Akshay Damle tomorrow at 9pm",
            "Call Aarav and also Akshay Damle tomorrow at 9pm",
            "Call Aarav from Appointments group and also Akshay Damle from that same grp tomorrow at 9pm",
        ):
            with self.subTest(prompt=prompt):
                resolved = self.post("/campaigns/resolve", {"raw_prompt": prompt})
                self.assertEqual(resolved.status_code, 200, resolved.json)
                self.assertEqual([c["id"] for c in resolved.json["selection"]["contacts"]], [ids[0], akshay])

    def test_selected_template_id_ignores_ambiguous_template_wording(self):
        group, ids = self.contacts()
        template = self.post("/templates", {"name": "Reminder", "message": "Please attend your appointment."}).json["template"]
        result = self.post("/campaigns/preview", {"group_id": group, "recipient_ids": ids[:1],
            "raw_prompt": "Use the template that is a reminder from the template section tomorrow at 3pm",
            "template_id": template["id"]})
        self.assertEqual(result.status_code, 200, result.json)
        self.assertEqual(result.json["campaign"]["template_name"], "Reminder")
        self.assertIn(template["message"], result.json["campaign"]["message"])

    def test_ambiguous_missing_and_unapproved_people_do_not_expand_to_whole_group(self):
        appointments, _, ids = self.named_groups()
        self.post(f"/groups/{appointments}/contacts", {"name": "Aarav Patel", "number": "9765432109", "consent_confirmed": True})
        for prompt in ("Call Aarav from Appointments tomorrow", "Call Missing from Appointments tomorrow", "Call Neha from Unknown group tomorrow"):
            result = self.post("/campaigns/resolve", {"raw_prompt": prompt})
            self.assertEqual(result.status_code, 400, result.json)
        self.client.patch(f"/groups/{appointments}/contacts/{ids[1]}", json={"opt_out": True}, headers=self.headers)
        self.assertEqual(self.post("/campaigns/resolve", {"raw_prompt": "Call Riya from Appointments tomorrow"}).status_code, 409)
        self.tts.assert_not_called()
        self.agent.assert_not_called()

    def test_resolution_is_account_scoped_and_phonebook_calls_are_unchanged(self):
        self.named_groups()
        for suffix in ("at 10:15", "at nine fifteen", "every 5 minutes starting tomorrow", "in 30 minutes"):
            response = self.post("/campaigns/resolve", {"raw_prompt": f"Call Aarav from Appointments group {suffix}"})
            self.assertEqual(response.status_code, 200, response.json)
            self.assertEqual(len(response.json["selection"]["contacts"]), 1)
        for prompt in ("Call Mom tomorrow at 3pm", "Call Mom in 30 minutes", "Call 9876543210 after 2 hours"):
            result = self.post("/campaigns/resolve", {"raw_prompt": prompt})
            self.assertEqual(result.json["status"], "not_group", result.json)
        self.user.return_value["id"] = "other-business"
        from app.models import AccountPlan
        with self.sessions() as db:
            db.add(AccountPlan(user_id="other-business", plan_id="business", credits_available=10))
            db.commit()
        self.assertEqual(self.post("/campaigns/resolve", {"raw_prompt": "Call Aarav from Appointments group tomorrow"}).status_code, 400)

    def test_cross_group_duplicates_and_optout_are_checked_again(self):
        appointments, customers, ids = self.named_groups()
        self.post(f"/groups/{customers}/contacts", {"name": "Aarav Sharma", "number": "9876543210", "consent_confirmed": True})
        response = self.post("/campaigns/resolve", {"raw_prompt": "Call Aarav from Appointments and Aarav from Customers tomorrow"})
        self.assertEqual(len(response.json["selection"]["contacts"]), 1)
        preview = self.post("/campaigns/preview", {"group_id": SELECTED_GROUPS, "recipient_ids": ids[:1], "raw_prompt": "Tomorrow at 3pm say hello"}).json["campaign"]
        self.post(f"/campaigns/{preview['id']}/schedule", {"confirmed": True})
        self.client.patch(f"/groups/{appointments}/contacts/{ids[0]}", json={"opt_out": True}, headers=self.headers)
        with self.sessions() as db:
            job = db.query(CallJob).filter_by(campaign_id=preview["id"]).one()
        with patch("app.tasks.call_task.get_db", self.sessions), patch("app.tasks.call_task.place_call") as place:
            from app.tasks.call_task import execute_call
            execute_call(job.id)
        place.assert_not_called()


class CampaignTimingTests(unittest.TestCase):
    def test_message_repetition_is_not_a_call_interval(self):
        self.assertEqual(interval_from_instruction("Tomorrow at 3pm tell them to take medicine every two hours", None, model_interpreted=True), {})

    def test_compound_interval_does_not_drop_minutes(self):
        self.assertEqual(interval_from_instruction("call each person every 1 hour and 30 minutes", {"amount": 90, "unit": "minute"}),
                         {"amount": 90, "unit": "minute"})

    def test_units_and_month_end(self):
        start = datetime(2028, 1, 31, 15, tzinfo=ZoneInfo("Asia/Kolkata"))
        for unit, delta in [("seconds", timedelta(seconds=2)), ("minutes", timedelta(minutes=2)),
                            ("hours", timedelta(hours=2)), ("days", timedelta(days=2)), ("weeks", timedelta(weeks=2))]:
            interval = interval_from_instruction(f"Call every 2 {unit}")
            self.assertEqual(recipient_time(start, interval, 1, "Asia/Kolkata"), start + delta)
        self.assertEqual(recipient_time(start, interval_from_instruction("every 1 month"), 1, "Asia/Kolkata").day, 29)
        self.assertEqual(recipient_time(start, interval_from_instruction("every 1 month"), 2, "Asia/Kolkata").day, 31)

    def test_invalid_intervals_are_not_silently_simultaneous(self):
        for prompt in ("every 0 minutes", "every -5 hours", "every few days", "every 1.5 months"):
            with self.assertRaises(PaymentError):
                interval_from_instruction(prompt)

    def test_model_interval_can_express_mixed_language(self):
        self.assertEqual(interval_from_instruction("har do ghante", {"amount": 2, "unit": "hour"}), {"amount": 2, "unit": "hour"})
