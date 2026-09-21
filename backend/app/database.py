from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import declarative_base, sessionmaker

from .config import Config


DATABASE_URL = Config.DATABASE_URL
CONNECT_ARGS = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}
engine = create_engine(
    DATABASE_URL,
    connect_args=CONNECT_ARGS,
    pool_pre_ping=True,
    future=True,
)
SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=engine,
    expire_on_commit=False,
)
Base = declarative_base()


def get_engine():
    return engine


def get_db():
    return SessionLocal()


def _call_jobs_column_definitions(dialect_name: str):
    timestamp_type = "TIMESTAMP WITH TIME ZONE" if dialect_name == "postgresql" else "DATETIME"
    current_timestamp = "NOW()" if dialect_name == "postgresql" else "CURRENT_TIMESTAMP"
    return {
        "user_id": "VARCHAR(255)",
        "contact_name": "VARCHAR(255)",
        "contact_number": "VARCHAR(20)",
        "original_message": "TEXT",
        "rephrased_message": "TEXT",
        "audio_url": "TEXT",
        "scheduled_time": timestamp_type,
        "status": "VARCHAR(20) DEFAULT 'pending'",
        "msg91_request_id": "VARCHAR(255)",
        "created_at": f"{timestamp_type} DEFAULT {current_timestamp}",
        "updated_at": f"{timestamp_type} DEFAULT {current_timestamp}",
    }


def ensure_database_schema():
    # create_all handles brand-new databases, while the ALTER loop patches legacy tables in-place.
    Base.metadata.create_all(bind=engine)

    inspector = inspect(engine)
    if "call_jobs" not in inspector.get_table_names():
        return

    existing_columns = {column["name"] for column in inspector.get_columns("call_jobs")}
    column_definitions = _call_jobs_column_definitions(engine.dialect.name)

    with engine.begin() as connection:
        for column_name, column_definition in column_definitions.items():
            if column_name not in existing_columns:
                # This lightweight migration approach is enough for a student project without Alembic.
                connection.execute(
                    text(f"ALTER TABLE call_jobs ADD COLUMN {column_name} {column_definition}")
                )
