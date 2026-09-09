import logging
import re
import xml.etree.ElementTree as ET

import requests

from ..config import Config


LOGGER = logging.getLogger(__name__)


def _normalized_public_base_url() -> str:
    public_base_url = Config.PUBLIC_BASE_URL.strip().rstrip("/")
    if not public_base_url:
        raise ValueError("PUBLIC_BASE_URL is required so Exotel can reach the backend callback endpoints.")
    return public_base_url


def _normalize_customer_number(contact_number: str) -> str:
    cleaned_number = re.sub(r"[^\d+]", "", str(contact_number).strip())
    # Exotel's current customer-to-flow docs use international format for the called customer number.
    if cleaned_number.startswith("+91") and len(cleaned_number) == 13:
        return cleaned_number
    if cleaned_number.startswith("91") and len(cleaned_number) == 12:
        return f"+{cleaned_number}"
    if len(cleaned_number) == 10 and cleaned_number.isdigit():
        return f"+91{cleaned_number}"
    if cleaned_number.startswith("0") and len(cleaned_number) == 11:
        return f"+91{cleaned_number[1:]}"
    raise ValueError("contact_number must be a valid Indian mobile number for Exotel outbound calling.")


def _build_status_callback_url() -> str:
    return f"{_normalized_public_base_url()}/webhook/exotel"


def _build_flow_url() -> str:
    if not Config.EXOTEL_FLOW_ID:
        raise ValueError("EXOTEL_FLOW_ID is required for the Exotel outbound flow integration.")
    # The user's tested Exotel setup uses a dashboard flow, so the API request must point at that flow entry URL.
    return f"http://my.exotel.com/{Config.EXOTEL_ACCOUNT_SID}/exoml/start_voice/{Config.EXOTEL_FLOW_ID}"


def _extract_call_sid(response: requests.Response) -> str:
    try:
        payload = response.json()
        call_payload = payload.get("Call") or payload.get("call") or {}
        call_sid = call_payload.get("Sid") or call_payload.get("sid")
        if call_sid:
            return str(call_sid)
    except ValueError:
        pass

    try:
        root = ET.fromstring(response.text)
        sid_node = root.find(".//Sid")
        if sid_node is not None and sid_node.text:
            return sid_node.text.strip()
    except ET.ParseError:
        pass

    raise ValueError(f"Exotel response did not include a call sid: {response.text}")


def place_call(contact_number: str, audio_url: str) -> str:
    if not all(
        [
            Config.EXOTEL_API_KEY,
            Config.EXOTEL_API_TOKEN,
            Config.EXOTEL_ACCOUNT_SID,
            Config.EXOTEL_CALLER_ID,
            Config.EXOTEL_FLOW_ID,
        ]
    ):
        raise ValueError(
            "Exotel credentials are missing. Set EXOTEL_API_KEY, EXOTEL_API_TOKEN, EXOTEL_ACCOUNT_SID, EXOTEL_CALLER_ID, and EXOTEL_FLOW_ID."
        )

    normalized_audio_url = str(audio_url or "").strip()
    if not normalized_audio_url.startswith(("http://", "https://")):
        raise ValueError("audio_url must be a public HTTP or HTTPS URL before Exotel can play it.")

    flow_url = _build_flow_url()
    normalized_contact_number = _normalize_customer_number(contact_number)

    # Using requests auth avoids logging credentials in the URL while still matching Exotel's basic auth requirement.
    response = requests.post(
        f"https://{Config.EXOTEL_BASE_URL}/v1/Accounts/{Config.EXOTEL_ACCOUNT_SID}/Calls/connect.json",
        auth=(Config.EXOTEL_API_KEY, Config.EXOTEL_API_TOKEN),
        data=[
            ("From", normalized_contact_number),
            ("CallerId", Config.EXOTEL_CALLER_ID),
            # Exotel connects the customer into the configured flow, and that flow should fetch /exotel-audio-url.
            ("Url", flow_url),
            # Exotel treats this outbound reminder flow as a transactional call.
            ("CallType", "trans"),
            ("StatusCallback", _build_status_callback_url()),
        ],
        timeout=60,
    )
    if not response.ok:
        LOGGER.error(
            "Exotel outbound call failed with status %s for customer %s using flow %s and hosted audio %s. Response body: %s",
            response.status_code,
            normalized_contact_number,
            flow_url,
            normalized_audio_url,
            response.text,
        )
        response.raise_for_status()

    call_sid = _extract_call_sid(response)
    LOGGER.info(
        "Exotel call created successfully for %s with sid %s. Flow %s must fetch the hosted audio URL %s through /exotel-audio-url.",
        normalized_contact_number,
        call_sid,
        flow_url,
        normalized_audio_url,
    )
    return str(call_sid)
