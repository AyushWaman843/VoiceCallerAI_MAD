import json
import unittest
from datetime import datetime
from unittest.mock import MagicMock, patch
from zoneinfo import ZoneInfo

from app.config import Config
from app.services.llm_service import process_message
from app.routes.calls import ScheduleCallRequest, _prepare_agent_result


class LlmEfficiencyTests(unittest.TestCase):
    def setUp(self):
        self.now = datetime(2026, 10, 27, 8, 0, tzinfo=ZoneInfo("Asia/Kolkata"))
        self.contacts = [{"name": f"Person {i}", "number": f"+919{i:09d}"} for i in range(500)]
        self.contacts.append({"name": "Mom", "number": "+919876543210"})
        self.payload = {"contact_name": "Mom", "message": "Bring the books.",
                        "rephrased_message": "Hi Mom, this is an automated message on behalf of Owner. Bring the books.",
                        "scheduled_time": "2026-10-28T15:00:00+05:30"}

    def run_agent(self, prompt, campaign=False):
        client = MagicMock()
        client.chat.completions.create.return_value.choices[0].message.content = json.dumps(self.payload)
        with patch.object(Config, "DEEPSEEK_API_KEY", "test"), patch("app.services.llm_service.OpenAI", return_value=client):
            result = process_message(prompt, "Owner", self.contacts, self.now, campaign=campaign)
        sent = client.chat.completions.create.call_args.kwargs["messages"][1]["content"]
        context = sent.split("Contacts [name,number]: ", 1)[1].split("\nRaw request:", 1)[0]
        return result, context

    def test_every_contact_and_number_retained_in_compact_input(self):
        result, context = self.run_agent("Call Mom tomorrow at 3pm and tell her to bring the books")
        self.assertEqual(json.loads(context), [[c["name"], c["number"]] for c in self.contacts])
        self.assertLess(len(context), len(json.dumps(self.contacts)))
        self.assertEqual(result["contact_number"], "+919876543210")
        self.assertEqual(result["missing_fields"], [])
        self.assertTrue(result["time_extracted"])

    def test_direct_number_and_fixed_campaign_skip_irrelevant_phonebook(self):
        result, context = self.run_agent("Call 9876543210 tomorrow at 3pm and say hello")
        self.assertEqual(context, "[]")
        self.assertEqual(result["contact_number"], "+919876543210")
        _, context = self.run_agent("Call selected recipients tomorrow at 3pm and say hello", campaign=True)
        self.assertEqual(context, "[]")

    def test_duplicate_names_with_different_numbers_are_preserved(self):
        self.contacts = [{"name": "Ayush", "number": "+919876543210"}, {"name": "Ayush", "number": "+919123456789"}]
        self.payload["contact_name"] = "Ayush"
        result, context = self.run_agent("Call Ayush tomorrow at 3pm")
        self.assertEqual(len(json.loads(context)), 2)
        self.assertIn("contact_number", result["missing_fields"])

    def test_filling_missing_time_uses_existing_message_without_model_request(self):
        existing = dict(self.payload, contact_number="+919876543210", scheduled_time=None)
        request = ScheduleCallRequest("owner", "Owner", "Call Mom and bring books", self.contacts,
            chosen_time="2026-10-28T15:00:00+05:30", agent_result=existing)
        with patch("app.routes.calls.process_message") as model:
            result = _prepare_agent_result(request, self.now)
        model.assert_not_called()
        self.assertEqual(result["message"], existing["message"])
        self.assertEqual(result["scheduled_time"], request.chosen_time)
        self.assertEqual(result["missing_fields"], [])
