import logging
import tempfile
from threading import Lock, Thread
from time import perf_counter
from pathlib import Path

import requests

from ..config import Config


LOGGER = logging.getLogger(__name__)
_LOCAL_MODEL = None
_MODEL_LOCK = Lock()


def _load_local_model():
    # Startup warmup and the first upload can arrive at the same time.
    with _MODEL_LOCK:
        return _initialize_local_model()


def _initialize_local_model():
    global _LOCAL_MODEL
    if _LOCAL_MODEL is not None:
        return _LOCAL_MODEL

    try:
        from faster_whisper import WhisperModel
    except ImportError as exc:
        raise RuntimeError(
            "Local Whisper is not installed yet. Install backend requirements to enable offline transcription."
        ) from exc

    _LOCAL_MODEL = WhisperModel(
        Config.LOCAL_WHISPER_MODEL,
        device=Config.LOCAL_WHISPER_DEVICE,
        compute_type=Config.LOCAL_WHISPER_COMPUTE_TYPE,
    )
    LOGGER.info(
        "Loaded local faster-whisper model %s on %s with compute type %s.",
        Config.LOCAL_WHISPER_MODEL,
        Config.LOCAL_WHISPER_DEVICE,
        Config.LOCAL_WHISPER_COMPUTE_TYPE,
    )
    return _LOCAL_MODEL


def warmup_transcription():
    if str(Config.TRANSCRIPTION_PROVIDER).strip().lower() != "local":
        return

    def load():
        try:
            _load_local_model()
        except Exception:
            LOGGER.exception("Whisper preload failed; the next upload will retry.")

    Thread(target=load, name="whisper-preload", daemon=True).start()


def transcription_hints(user_id):
    # Vocabulary hints are account-scoped, bounded, and never select call recipients.
    from sqlalchemy import literal, select, union_all
    from ..database import get_db
    from ..models import ContactGroup, GroupContact, MessageTemplate

    try:
        with get_db() as db:
            # One database round trip; group/template names precede contact hints.
            vocabulary = union_all(*[
                select(model.name.label("name"), literal(priority).label("priority"))
                .where(model.user_id == user_id)
                for priority, model in enumerate((ContactGroup, MessageTemplate, GroupContact))
            ]).order_by("priority", "name").limit(80)
            names = [row[0] for row in db.execute(vocabulary)]
        return ", ".join(dict.fromkeys(names))[:500] or None
    except Exception:
        LOGGER.warning("Transcribing without saved-name hints: vocabulary lookup failed.")
        return None


def _transcribe_with_local_whisper(file_storage, hotwords=None) -> str:
    started = perf_counter()
    model = _load_local_model()

    suffix = Path(file_storage.filename or "prompt.m4a").suffix or ".m4a"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_audio:
        temp_path = Path(temp_audio.name)
        file_storage.stream.seek(0)
        temp_audio.write(file_storage.stream.read())

    try:
        segments, _ = model.transcribe(
            str(temp_path),
            task="transcribe",
            beam_size=5,
            vad_filter=True,
            vad_parameters={"min_silence_duration_ms": 500, "speech_pad_ms": 400},
            without_timestamps=True,
            hotwords=hotwords,
        )
        text = " ".join(segment.text.strip() for segment in segments).strip()
    finally:
        temp_path.unlink(missing_ok=True)

    if not text:
        raise RuntimeError("Local Whisper returned an empty transcription.")
    LOGGER.info("Local transcription completed in %.2fs.", perf_counter() - started)
    return text


def _transcribe_with_openai(file_storage) -> str:
    if not Config.OPENAI_API_KEY:
        raise RuntimeError("OPENAI_API_KEY is required when TRANSCRIPTION_PROVIDER=openai.")

    file_storage.stream.seek(0)
    response = requests.post(
        "https://api.openai.com/v1/audio/transcriptions",
        headers={"Authorization": f"Bearer {Config.OPENAI_API_KEY}"},
        data={"model": Config.WHISPER_MODEL},
        files={
            "file": (
                file_storage.filename or "prompt.m4a",
                file_storage.stream,
                file_storage.mimetype or "application/octet-stream",
            )
        },
        timeout=120,
    )

    if not response.ok:
        LOGGER.error(
            "OpenAI Whisper transcription failed with status %s for file %s and model %s. Response: %s",
            response.status_code,
            file_storage.filename,
            Config.WHISPER_MODEL,
            response.text[:500],
        )
        response.raise_for_status()

    text = str((response.json() or {}).get("text") or "").strip()
    if not text:
        raise RuntimeError("OpenAI Whisper returned an empty transcription.")
    return text


def transcribe_audio(file_storage, hotwords=None) -> str:
    if file_storage is None or not getattr(file_storage, "filename", ""):
        raise ValueError("An audio file is required.")

    provider = str(Config.TRANSCRIPTION_PROVIDER or "local").strip().lower()
    if provider == "openai":
        return _transcribe_with_openai(file_storage)

    return _transcribe_with_local_whisper(file_storage, hotwords=hotwords)
