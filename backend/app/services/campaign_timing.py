import calendar
import math
import re
from datetime import timedelta
from zoneinfo import ZoneInfo

from ..routes.payments import PaymentError


def interval_from_instruction(prompt, extracted=None, model_interpreted=False):
    # "Tell them to take medicine every two hours" is message content, not a call interval.
    if model_interpreted and extracted is None and not re.search(
        r'\b(?:each\s+(?:person|user|recipient|contact|member)|between\s+(?:calls|recipients)|stagger|apart|intervals?)\b', prompt, re.I
    ):
        return {}
    # Deterministic override handles common intervals without another model request.
    match = re.search(r'\b(?:every|apart by|at intervals of)\s+(\d+(?:\.\d+)?|a|an|one|two|three|four|five|ten|fifteen|thirty)\s*(seconds?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?)\b', prompt, re.I)
    value = extracted or {}
    if match:
        if re.match(r'\s*(?:and\s+)?\d+\s*(?:seconds?|minutes?|hours?|days?)\b', prompt[match.end():], re.I):
            # A compound duration must use the model's combined value, never just its first unit.
            if not extracted:
                raise PaymentError("Please express the gap as one duration, for example every 90 minutes.")
            match = None
    if match:
        words = dict(a=1, an=1, one=1, two=2, three=3, four=4, five=5, ten=10, fifteen=15, thirty=30)
        amount = match[1].lower()
        unit = match[2].lower().rstrip("s")
        value = dict(amount=words.get(amount, amount), unit={"min": "minute", "hr": "hour"}.get(unit, unit))
    if not value:
        if re.search(r'\b(every|stagger|apart|interval)\b', prompt, re.I):
            raise PaymentError("Please specify the gap between recipients, for example every 5 minutes, and when the first call should start.")
        return {}
    try:
        amount, unit = float(value["amount"]), value["unit"]
        if not math.isfinite(amount) or amount <= 0 or unit not in {"second", "minute", "hour", "day", "week", "month", "year"}:
            raise ValueError()
        if unit in {"month", "year"} and not amount.is_integer():
            raise ValueError()
    except (TypeError, KeyError, ValueError):
        raise PaymentError("Specify a positive gap in seconds, minutes, hours, days, weeks, months or whole years.") from None
    return dict(amount=amount, unit=unit)


def recipient_time(start, interval, index, timezone):
    if start.tzinfo is None:
        # SQLite strips offsets; persisted call times are IST throughout this backend.
        start = start.replace(tzinfo=ZoneInfo("Asia/Kolkata"))
    local = start.astimezone(ZoneInfo(timezone))
    if not interval or index == 0:
        return local
    amount, unit = interval["amount"] * index, interval["unit"]
    try:
        if unit in {"month", "year"}:
            months = int(amount) * (12 if unit == "year" else 1)
            year, month = divmod(local.year * 12 + local.month - 1 + months, 12)
            return local.replace(year=year, month=month + 1, day=min(local.day, calendar.monthrange(year, month + 1)[1]))
        return local + timedelta(**{unit + "s": amount})
    except (ValueError, OverflowError):
        raise PaymentError("The requested campaign interval extends beyond a valid calendar date.") from None
