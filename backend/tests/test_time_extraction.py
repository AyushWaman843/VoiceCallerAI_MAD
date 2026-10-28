import unittest
from datetime import datetime
from zoneinfo import ZoneInfo

from app.services.llm_service import _extract_clear_relative_time


class ClearTimeExtractionTests(unittest.TestCase):
    def setUp(self):
        self.now = datetime(2026, 10, 26, 20, 0, tzinfo=ZoneInfo("Asia/Kolkata"))

    def test_tomorrow_at_four_pm(self):
        self.assertEqual(_extract_clear_relative_time("call Mom tomorrow at 4:30 pm", self.now),
                         "2026-10-27T16:30:00+05:30")

    def test_after_two_hours(self):
        self.assertEqual(_extract_clear_relative_time("call Ayush after 2 hours", self.now),
                         "2026-10-26T22:00:00+05:30")

    def test_next_week_tuesday(self):
        self.assertEqual(_extract_clear_relative_time("next week Tuesday at 10 am", self.now),
                         "2026-11-03T10:00:00+05:30")


if __name__ == "__main__":
    unittest.main()
