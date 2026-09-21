import json
import logging
import re
from difflib import SequenceMatcher

from openai import OpenAI

from ..config import Config
from ..utils.helpers import ensure_ist_datetime


LOGGER = logging.getLogger(__name__)
PHONE_PATTERN = re.compile(r"(?<!\d)(?:\+?91[\s-]?|0)?[6-9](?:[\s-]?\d){9}(?!\d)")
GENERIC_CONTACT_NAMES = {
    "contact",
    "customer",
    "her",
    "him",
    "none",
    "not provided",
    "recipient",
    "them",
    "unknown",
}


def normalize_indian_number(number: str) -> str:
    digits = re.sub(r"\D", "", str(number or ""))
    if len(digits) == 10 and digits[0] in "6789":
        return f"+91{digits}"
    if len(digits) == 11 and digits.startswith("0") and digits[1] in "6789":
        return f"+91{digits[1:]}"
    if len(digits) == 12 and digits.startswith("91") and digits[2] in "6789":
        return f"+{digits}"
    return ""


def _extract_prompt_number(raw_prompt: str) -> str:
    phone_match = PHONE_PATTERN.search(raw_prompt)
    return normalize_indian_number(phone_match.group(0)) if phone_match else ""


def _normalize_name(value: str) -> str:
    return " ".join(re.findall(r"[a-z0-9]+", str(value or "").lower()))


def _normalize_contacts(contacts: list[dict]) -> list[dict]:
    normalized_contacts = []
    for contact in contacts:
        if not isinstance(contact, dict):
            continue
        name = str(contact.get("name") or "").strip()
        if not name:
            continue
        normalized_contacts.append(
            {
                "name": name,
                "number": normalize_indian_number(str(contact.get("number") or "")),
            }
        )
    return normalized_contacts


def _contact_score(requested_name: str, contact_name: str) -> float:
    requested = _normalize_name(requested_name)
    candidate = _normalize_name(contact_name)
    if not requested or not candidate:
        return 0.0
    if requested == candidate:
        return 1.0

    requested_words = set(requested.split())
    candidate_words = set(candidate.split())
    if requested_words and requested_words.issubset(candidate_words):
        return 0.9
    if candidate_words and candidate_words.issubset(requested_words):
        return 0.88
    return SequenceMatcher(None, requested, candidate).ratio()


def _match_contact(requested_name: str, contacts: list[dict]) -> dict | None:
    if not requested_name or not contacts:
        return None

    ranked_contacts = sorted(
        ((_contact_score(requested_name, contact["name"]), contact) for contact in contacts),
        key=lambda item: item[0],
        reverse=True,
    )
    best_score, best_contact = ranked_contacts[0]
    second_score = ranked_contacts[1][0] if len(ranked_contacts) > 1 else 0.0
    # A close runner-up makes short names such as "Ayush" ambiguous instead of choosing silently.
    if best_score < 0.62 or (second_score >= 0.62 and best_score - second_score < 0.08):
        return None
    return best_contact


def _find_contact_in_prompt(raw_prompt: str, contacts: list[dict]) -> dict | None:
    prompt_words = set(_normalize_name(raw_prompt).split())
    possible_matches = []
    for contact in contacts:
        contact_words = set(_normalize_name(contact["name"]).split())
        meaningful_words = {word for word in contact_words if len(word) >= 3}
        if meaningful_words and meaningful_words.intersection(prompt_words):
            possible_matches.append(contact)
    return possible_matches[0] if len(possible_matches) == 1 else None


def _build_spoken_message(user_name: str, contact_name: str, message: str) -> str:
    spoken_contact = contact_name or "there"
    cleaned_message = " ".join(str(message or "").strip().split())
    prefix = f"Hi {spoken_contact}, this is an automated message on behalf of {user_name}."
    if not cleaned_message:
        return prefix
    punctuation = "" if cleaned_message.endswith((".", "!", "?")) else "."
    return f"{prefix} {cleaned_message}{punctuation}"


def _extract_json_payload(raw_content: str) -> dict:
    stripped_content = raw_content.strip()
    if stripped_content.startswith("```"):
        stripped_content = re.sub(r"^```(?:json)?\s*", "", stripped_content)
        stripped_content = re.sub(r"\s*```$", "", stripped_content)
    json_match = re.search(r"\{.*\}", stripped_content, re.DOTALL)
    if not json_match:
        raise ValueError("LLM response did not contain a JSON object.")
    return json.loads(json_match.group(0))


def _normalize_result(
    payload: dict,
    raw_prompt: str,
    user_name: str,
    contacts: list[dict],
    direct_number: str,
) -> dict:
    requested_name = str(payload.get("contact_name") or "").strip()
    core_message = str(payload.get("message") or "").strip()
    normalized_contacts = _normalize_contacts(contacts)

    if direct_number:
        normalized_requested_name = _normalize_name(requested_name)
        requested_words = set(normalized_requested_name.split())
        prompt_words = set(_normalize_name(raw_prompt).split())
        name_is_present = bool({word for word in requested_words if len(word) >= 2}.intersection(prompt_words))
        contact_name = (
            requested_name
            if name_is_present and normalized_requested_name not in GENERIC_CONTACT_NAMES
            else ""
        )
        contact_name = contact_name or direct_number
        contact_number = direct_number
    else:
        matched_contact = _match_contact(requested_name, normalized_contacts)
        if matched_contact is None:
            matched_contact = _find_contact_in_prompt(raw_prompt, normalized_contacts)
        contact_name = matched_contact["name"] if matched_contact else ""
        contact_number = matched_contact["number"] if matched_contact else ""

    scheduled_time = None
    if payload.get("scheduled_time"):
        try:
            scheduled_time = ensure_ist_datetime(str(payload["scheduled_time"])).isoformat()
        except ValueError:
            LOGGER.warning("DeepSeek returned an invalid scheduled_time: %s", payload.get("scheduled_time"))

    time_extracted = bool(payload.get("time_extracted") and scheduled_time)
    rephrased_message = str(payload.get("rephrased_message") or "").strip()
    required_prefix = f"Hi {contact_name or 'there'}, this is an automated message on behalf of {user_name}."
    if not rephrased_message.startswith(required_prefix):
        rephrased_message = _build_spoken_message(user_name, contact_name, core_message)

    missing_fields = []
    if not contact_name:
        missing_fields.append("contact_name")
    if not contact_number:
        missing_fields.append("contact_number")
    if not core_message:
        missing_fields.append("message")
    if not scheduled_time:
        missing_fields.append("scheduled_time")

    return {
        "contact_name": contact_name,
        "contact_number": contact_number,
        "message": core_message,
        "rephrased_message": rephrased_message,
        "scheduled_time": scheduled_time,
        "time_extracted": time_extracted,
        "missing_fields": missing_fields,
    }


def process_message(raw_prompt: str, user_name: str, contacts: list[dict], current_time_ist) -> dict:
    direct_number = _extract_prompt_number(raw_prompt)
    fallback_payload = {
        "contact_name": "",
        "message": raw_prompt,
        "rephrased_message": "",
        "scheduled_time": None,
        "time_extracted": False,
    }

    if not Config.DEEPSEEK_API_KEY:
        LOGGER.warning("DEEPSEEK_API_KEY is not configured, so only deterministic agent extraction is available.")
        return _normalize_result(fallback_payload, raw_prompt, user_name, contacts, direct_number)

    client = OpenAI(api_key=Config.DEEPSEEK_API_KEY, base_url="https://api.deepseek.com")
    system_prompt = (
        "You are a call-scheduling extraction agent. Return ONLY one valid JSON object with exactly these keys: "
        '"contact_name", "contact_number", "message", "rephrased_message", "scheduled_time", '
        '"time_extracted", and "missing_fields". '
        "Identify who should be called, extract only the core message to speak, and resolve the requested time using "
        "the supplied current IST timestamp. Understand tomorrow, day after tomorrow, next Friday, this Saturday, "
        "after N hours, in N minutes, and next week plus a weekday and time. scheduled_time must be ISO8601 with "
        "+05:30, or null when absent. time_extracted is true only when the prompt contains a resolvable time. "
        "Choose contact_name only from the supplied contacts when a clear match exists. If a phone number appears "
        "directly in the prompt, use it and do not require a contacts match; use the person's stated name, or the "
        "number itself if no name is stated. contact_number should be +91 followed by 10 digits when known. "
        "rephrased_message must sound natural and start exactly with: "
        f'"Hi [contact_name], this is an automated message on behalf of {user_name}." '
        "Do not speak the scheduling instruction or call command. missing_fields must list only genuinely missing "
        "values from contact_name, contact_number, message, and scheduled_time."
    )
    user_prompt = (
        f"Current IST time: {current_time_ist.isoformat()}\n"
        f"Logged-in user name: {user_name}\n"
        f"Contacts: {json.dumps(contacts, ensure_ascii=True)}\n"
        f"Raw request: {raw_prompt}"
    )

    try:
        response = client.chat.completions.create(
            model="deepseek-chat",
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.0,
        )
        raw_content = response.choices[0].message.content or ""
        payload = _extract_json_payload(raw_content)
        return _normalize_result(payload, raw_prompt, user_name, contacts, direct_number)
    except Exception:
        LOGGER.exception("DeepSeek agent processing failed, so deterministic fallback extraction is being used.")
        return _normalize_result(fallback_payload, raw_prompt, user_name, contacts, direct_number)
