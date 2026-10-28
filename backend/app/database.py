from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import declarative_base, sessionmaker

from .config import Config


DATABASE_URL = Config.DATABASE_URL
CONNECT_ARGS = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {"connect_timeout": 5}
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
        "campaign_id": "VARCHAR(36)",
        "credit_state": "VARCHAR(20)",
        "credit_reservation_id": "VARCHAR(100)",
        "created_at": f"{timestamp_type} DEFAULT {current_timestamp}",
        "updated_at": f"{timestamp_type} DEFAULT {current_timestamp}",
    }


def ensure_database_schema():
    # create_all handles brand-new databases, while the ALTER loop patches legacy tables in-place.
    Base.metadata.create_all(bind=engine)
    if engine.dialect.name == "postgresql":
        # Purchases are backend-only; Supabase clients must never grant themselves credits.
        with engine.begin() as connection:
            connection.execute(text("ALTER TABLE call_jobs ENABLE ROW LEVEL SECURITY"))
            connection.execute(text("REVOKE ALL ON call_jobs FROM anon, authenticated"))
            connection.execute(text("ALTER TABLE test_credit_purchases ENABLE ROW LEVEL SECURITY"))
            connection.execute(text("REVOKE ALL ON test_credit_purchases FROM anon, authenticated"))
            for table_name in ("account_plans", "credit_events", "contact_groups", "group_contacts", "contact_imports", "campaigns", "audio_previews", "message_templates"):
                connection.execute(text(f"ALTER TABLE {table_name} ENABLE ROW LEVEL SECURITY"))
                connection.execute(text(f"REVOKE ALL ON {table_name} FROM anon, authenticated"))

    inspector = inspect(engine)
    if "campaigns" in inspector.get_table_names() and "interval_json" not in {c["name"] for c in inspector.get_columns("campaigns")}:
        with engine.begin() as connection:
            connection.execute(text("ALTER TABLE campaigns ADD COLUMN interval_json TEXT NOT NULL DEFAULT '{}'"))
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
        if engine.dialect.name == "postgresql":
            connection.execute(text("ALTER TABLE call_jobs DROP CONSTRAINT IF EXISTS check_call_job_status"))
            connection.execute(text("ALTER TABLE call_jobs ADD CONSTRAINT check_call_job_status "
                                    "CHECK (status IN ('pending', 'calling', 'connected', 'missed', 'failed', 'cancelled'))"))
        elif engine.dialect.name == "sqlite":
            old_status = "status IN ('pending', 'calling', 'connected', 'failed', 'cancelled')"
            ddl = connection.execute(text("SELECT sql FROM sqlite_master WHERE type='table' AND name='call_jobs'")).scalar()
            if ddl and old_status in ddl:
                indexes = connection.execute(text("SELECT sql FROM sqlite_master WHERE type='index' "
                    "AND tbl_name='call_jobs' AND sql IS NOT NULL")).scalars().all()
                revised = ddl.replace("call_jobs", "call_jobs_migrating", 1).replace(old_status,
                    "status IN ('pending', 'calling', 'connected', 'missed', 'failed', 'cancelled')")
                connection.execute(text(revised))
                columns = ", ".join(f'"{column["name"]}"' for column in inspect(connection).get_columns("call_jobs"))
                connection.execute(text(f"INSERT INTO call_jobs_migrating ({columns}) SELECT {columns} FROM call_jobs"))
                connection.execute(text("DROP TABLE call_jobs"))
                connection.execute(text("ALTER TABLE call_jobs_migrating RENAME TO call_jobs"))
                for index_sql in indexes:
                    connection.execute(text(index_sql))
