import unittest
from unittest.mock import patch

from sqlalchemy import create_engine, text

from app import database
from app.models import CallJob
from app.routes.payments import CreditPurchase


class SchemaMigrationTests(unittest.TestCase):
    def test_campaign_interval_migration_preserves_existing_data_and_is_repeatable(self):
        engine = create_engine("sqlite://")
        with engine.begin() as db:
            db.execute(text("CREATE TABLE campaigns (id VARCHAR(36) PRIMARY KEY, message TEXT)"))
            db.execute(text("INSERT INTO campaigns (id, message) VALUES ('old', 'Keep this message')"))
        with patch.object(database, "engine", engine):
            database.ensure_database_schema()
            database.ensure_database_schema()
        with engine.begin() as db:
            self.assertEqual(db.execute(text("SELECT message, interval_json FROM campaigns WHERE id='old'")).one(),
                             ("Keep this message", "{}"))
            self.assertIn("message_templates", database.inspect(engine).get_table_names())
        engine.dispose()

    def test_existing_sqlite_jobs_keep_rows_and_accept_missed(self):
        engine = create_engine("sqlite://")
        with engine.begin() as db:
            db.execute(text("CREATE TABLE call_jobs (id VARCHAR(36) PRIMARY KEY, status VARCHAR(20), "
                            "created_at DATETIME, updated_at DATETIME, "
                            "CONSTRAINT check_call_job_status CHECK "
                            "(status IN ('pending', 'calling', 'connected', 'failed', 'cancelled')))"))
            db.execute(text("CREATE INDEX old_status_index ON call_jobs (status)"))
            db.execute(text("INSERT INTO call_jobs (id, status) VALUES ('existing', 'pending')"))
        with patch.object(database, "engine", engine):
            database.ensure_database_schema()
        with engine.begin() as db:
            self.assertEqual(db.execute(text("SELECT status FROM call_jobs WHERE id='existing'")).scalar(), "pending")
            db.execute(text("UPDATE call_jobs SET status='missed' WHERE id='existing'"))
            self.assertEqual(db.execute(text("SELECT status FROM call_jobs WHERE id='existing'")).scalar(), "missed")
            self.assertIsNotNone(db.execute(text("SELECT name FROM sqlite_master WHERE name='old_status_index'")).scalar())
        engine.dispose()


if __name__ == "__main__":
    unittest.main()
