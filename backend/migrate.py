from app.database import ensure_database_schema
from app.models import CallJob


def migrate() -> None:
    # Importing the model above ensures SQLAlchemy has the table metadata before the schema sync runs.
    _ = CallJob
    ensure_database_schema()


if __name__ == "__main__":
    migrate()
