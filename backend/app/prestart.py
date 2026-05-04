import re
import time

from sqlalchemy import create_engine, text
from sqlalchemy.engine import make_url
from sqlalchemy.exc import OperationalError

from app.core.config import settings
from app.db.base import Base
from app.db.session import engine
from app.models import user  # noqa: F401

MAX_RETRIES = 30
RETRY_DELAY_SECONDS = 3


def _validated_database_name(database_name: str | None) -> str:
    if not database_name:
        raise RuntimeError("DATABASE_URL must include a database name.")
    if not re.fullmatch(r"[A-Za-z0-9_]+", database_name):
        raise RuntimeError(
            "Database name must only contain letters, numbers, and underscores."
        )
    return database_name


def wait_for_database() -> None:
    url = make_url(settings.DATABASE_URL)
    database_name = _validated_database_name(url.database)
    admin_engine = create_engine(
        url.set(database="master"),
        pool_pre_ping=True,
        isolation_level="AUTOCOMMIT",
    )

    try:
        last_error: Exception | None = None
        for attempt in range(1, MAX_RETRIES + 1):
            try:
                with admin_engine.connect() as connection:
                    connection.execute(
                        text(
                            f"IF DB_ID(N'{database_name}') IS NULL "
                            f"CREATE DATABASE [{database_name}]"
                        )
                    )
                return
            except OperationalError as exc:
                last_error = exc
                print(
                    f"SQL Server is not ready yet "
                    f"({attempt}/{MAX_RETRIES}). Retrying in "
                    f"{RETRY_DELAY_SECONDS}s..."
                )
                time.sleep(RETRY_DELAY_SECONDS)

        raise RuntimeError("Could not connect to SQL Server in time.") from last_error
    finally:
        admin_engine.dispose()


def init_db() -> None:
    wait_for_database()
    Base.metadata.create_all(bind=engine)


if __name__ == "__main__":
    init_db()
