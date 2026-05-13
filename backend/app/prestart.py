import re
import time

from sqlalchemy import create_engine, text
from sqlalchemy.engine import make_url
from sqlalchemy.exc import OperationalError

from app.core.config import settings
from app.db.base import Base
from app.db.seed import seed_static_content
from app.db.session import engine
from app import models  # noqa: F401

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


def _add_column_if_missing(connection, table_name: str, column_name: str, definition: str) -> None:
    connection.execute(
        text(
            f"""
            IF COL_LENGTH('{table_name}', '{column_name}') IS NULL
            BEGIN
                ALTER TABLE {table_name} ADD {column_name} {definition}
            END
            """
        )
    )


def _alter_column_if_exists(connection, table_name: str, column_name: str, definition: str) -> None:
    connection.execute(
        text(
            f"""
            IF COL_LENGTH('{table_name}', '{column_name}') IS NOT NULL
            BEGIN
                ALTER TABLE {table_name} ALTER COLUMN {column_name} {definition}
            END
            """
        )
    )


def apply_additive_migrations() -> None:
    with engine.begin() as connection:
        _add_column_if_missing(connection, "users", "password", "NVARCHAR(255) NULL")
        _add_column_if_missing(connection, "session_questions", "question_id", "NVARCHAR(64) NULL")
        _add_column_if_missing(connection, "session_questions", "used_hint", "INT NULL")
        _alter_column_if_exists(connection, "games", "name", "NVARCHAR(255) NOT NULL")
        _alter_column_if_exists(connection, "games", "difficulty_level", "NVARCHAR(50) NULL")
        _alter_column_if_exists(connection, "game_content", "content_type", "NVARCHAR(50) NOT NULL")
        _alter_column_if_exists(connection, "game_content", "media_path", "NVARCHAR(500) NULL")
        _alter_column_if_exists(connection, "game_content", "question_text", "NVARCHAR(MAX) NULL")
        _alter_column_if_exists(connection, "game_content", "correct_answer", "NVARCHAR(100) NULL")
        _alter_column_if_exists(connection, "game_content", "emotion", "NVARCHAR(100) NULL")
        _alter_column_if_exists(connection, "game_content", "explanation", "NVARCHAR(MAX) NULL")
        _alter_column_if_exists(connection, "emotion_concepts", "emotion", "NVARCHAR(100) NOT NULL")
        _alter_column_if_exists(connection, "emotion_concepts", "title", "NVARCHAR(255) NOT NULL")
        _alter_column_if_exists(connection, "emotion_concepts", "video_path", "NVARCHAR(500) NULL")
        _alter_column_if_exists(connection, "emotion_concepts", "image_path", "NVARCHAR(500) NULL")
        _alter_column_if_exists(connection, "emotion_concepts", "audio_path", "NVARCHAR(500) NULL")
        _alter_column_if_exists(connection, "emotion_concepts", "description", "NVARCHAR(MAX) NULL")


def init_db() -> None:
    wait_for_database()
    Base.metadata.create_all(bind=engine)
    apply_additive_migrations()
    from app.db.session import SessionLocal

    db = SessionLocal()
    try:
        seed_static_content(db)
    finally:
        db.close()


if __name__ == "__main__":
    init_db()
