import logging
import shutil
import subprocess
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


def _convert_for_exotel(source_path: Path, output_path: Path) -> None:
    ffmpeg_path = shutil.which("ffmpeg")
    if not ffmpeg_path:
        raise RuntimeError("ffmpeg is required to convert ElevenLabs audio to Exotel's 8 kHz mono format.")

    result = subprocess.run(
        [
            ffmpeg_path,
            "-y",
            "-loglevel",
            "error",
            "-i",
            str(source_path),
            "-ar",
            "8000",
            "-ac",
            "1",
            "-c:a",
            "pcm_s16le",
            str(output_path),
        ],
        capture_output=True,
        text=True,
        timeout=60,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(f"Failed to convert audio for Exotel: {result.stderr.strip()}")


def generate_audio(text: str) -> str:
    if not Config.ELEVENLABS_API_KEY or not Config.ELEVENLABS_VOICE_ID:
        raise RuntimeError("ElevenLabs credentials are not configured.")
    if not Config.PUBLIC_BASE_URL.strip().startswith("https://"):
        raise RuntimeError("PUBLIC_BASE_URL must be a reachable HTTPS backend before generating audio.")
    AUDIO_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

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

    audio_id = uuid.uuid4()
    source_path = AUDIO_OUTPUT_DIR / f"{audio_id}.mp3"
    audio_path = AUDIO_OUTPUT_DIR / f"{audio_id}.wav"
    source_path.write_bytes(response.content)
    try:
        # Exotel's dynamic Greeting applet accepts 8 kHz, mono, 16-bit audio.
        _convert_for_exotel(source_path, audio_path)
    finally:
        source_path.unlink(missing_ok=True)
    return _build_public_audio_url(audio_path.name) or str(audio_path)
