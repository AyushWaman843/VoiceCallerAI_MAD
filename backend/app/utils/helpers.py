import re
from datetime import datetime, timedelta

import pytz

from ..config import Config


IST_TIMEZONE = pytz.timezone(Config.TIMEZONE or "Asia/Kolkata")


def get_current_ist():
    return datetime.now(IST_TIMEZONE)


def ensure_ist_datetime(value):
    if isinstance(value, datetime):
        parsed_datetime = value
    elif isinstance(value, str):
        normalized_value = value.strip().replace("Z", "+00:00")
        try:
            parsed_datetime = datetime.fromisoformat(normalized_value)
        except ValueError as exc:
            raise ValueError("chosen_time must be a valid ISO8601 datetime string.") from exc
    else:
        raise ValueError("Scheduled time must be a datetime object or ISO8601 string.")

    if parsed_datetime.tzinfo is None:
        return IST_TIMEZONE.localize(parsed_datetime)
    return parsed_datetime.astimezone(IST_TIMEZONE)


def validate_indian_number(number: str) -> bool:
    return bool(re.fullmatch(r"\+91\d{10}", str(number).strip()))


def is_at_least_two_minutes_ahead(scheduled_time) -> bool:
    scheduled_time_ist = ensure_ist_datetime(scheduled_time)
    return scheduled_time_ist >= get_current_ist() + timedelta(minutes=2)
