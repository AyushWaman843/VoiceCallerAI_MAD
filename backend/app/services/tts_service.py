import logging
import uuid
from pathlib import Path
from urllib.parse import quote

import requests

from ..config import Config


LOGGER = logging.getLogger(__name__)
AUDIO_OUTPUT_DIR = Path(__file__).resolve().parents[2] / "generated_audio"


def _build_public_audio_url(filename: str) -> str:
    public_base_url = Config.PUBLIC_BASE_URL.strip().rstrip("/")
    if public_base_url:
        # Only the filename is exposed here, which keeps the public media route simple and predictable.
        return f"{public_base_url}/media/{quote(filename)}"
    return ""


def generate_audio(text: str) -> str:
    # Phase 4 - wire this up after MSG91 is tested.
    AUDIO_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    if not Config.ELEVENLABS_API_KEY or not Config.ELEVENLABS_VOICE_ID:
        placeholder_path = AUDIO_OUTPUT_DIR / f"{uuid.uuid4()}.txt"
        placeholder_path.write_text(text, encoding="utf-8")
        LOGGER.warning("ElevenLabs credentials are missing, so a placeholder text file was created instead of audio.")
        return _build_public_audio_url(placeholder_path.name) or str(placeholder_path)

    response = requests.post(
        f"https://api.elevenlabs.io/v1/text-to-speech/{Config.ELEVENLABS_VOICE_ID}",
        headers={
            "xi-api-key": Config.ELEVENLABS_API_KEY,
            "Accept": "audio/mpeg",
            "Content-Type": "application/json",
        },
        json={
            "text": text,
            # The model is configurable so the backend can match a known-good manual ElevenLabs request.
            "model_id": Config.ELEVENLABS_MODEL_ID,
        },
        timeout=60,
    )
    if not response.ok:
        LOGGER.error(
            "ElevenLabs TTS failed with status %s for voice %s and model %s. Response: %s",
            response.status_code,
            Config.ELEVENLABS_VOICE_ID,
            Config.ELEVENLABS_MODEL_ID,
            response.text[:500],
        )
        response.raise_for_status()

    audio_path = AUDIO_OUTPUT_DIR / f"{uuid.uuid4()}.mp3"
    audio_path.write_bytes(response.content)
    return _build_public_audio_url(audio_path.name) or str(audio_path)
