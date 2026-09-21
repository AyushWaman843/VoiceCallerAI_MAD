import os
from pathlib import Path

from dotenv import load_dotenv


BASE_DIR = Path(__file__).resolve().parents[1]
load_dotenv(BASE_DIR / ".env")


def _normalize_database_url(database_url: str) -> str:
    # Supabase sometimes provides postgres:// URLs, while SQLAlchemy expects postgresql://.
    if database_url.startswith("postgres://"):
        return database_url.replace("postgres://", "postgresql://", 1)
    return database_url


class Config:
    DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
    ELEVENLABS_API_KEY = os.getenv("ELEVENLABS_API_KEY", "")
    ELEVENLABS_VOICE_ID = os.getenv("ELEVENLABS_VOICE_ID", "")
    ELEVENLABS_MODEL_ID = os.getenv("ELEVENLABS_MODEL_ID", "eleven_flash_v2_5")
    EXOTEL_API_KEY = os.getenv("EXOTEL_API_KEY", "")
    EXOTEL_API_TOKEN = os.getenv("EXOTEL_API_TOKEN", "")
    EXOTEL_ACCOUNT_SID = os.getenv("EXOTEL_ACCOUNT_SID", "")
    EXOTEL_CALLER_ID = os.getenv("EXOTEL_CALLER_ID", "")
    EXOTEL_BASE_URL = os.getenv("EXOTEL_BASE_URL") or os.getenv("EXOTEL_SUBDOMAIN", "api.exotel.com")
    EXOTEL_FLOW_ID = os.getenv("EXOTEL_FLOW_ID", "")
    DATABASE_URL = _normalize_database_url(os.getenv("DATABASE_URL", "sqlite:///voicecaller.db"))
    # A background job cannot infer the current request host, so we keep the public callback base URL in env.
    PUBLIC_BASE_URL = os.getenv("PUBLIC_BASE_URL", "")
    TIMEZONE = os.getenv("TIMEZONE", "Asia/Kolkata")
    SECRET_KEY = os.getenv("SECRET_KEY", "dev-secret-key")
