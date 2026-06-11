import json
import re
import time
from urllib.parse import unquote_plus

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


def _database_name_from_url(url) -> str | None:
    if url.database:
        return url.database

    odbc_connect = url.query.get("odbc_connect")
    if not odbc_connect:
        return None

    for part in unquote_plus(str(odbc_connect)).split(";"):
        key, _, value = part.partition("=")
        if key.strip().lower() in {"database", "initial catalog"}:
            return value.strip()
    return None


def _odbc_connect_with_database(connect_string: str, database_name: str) -> str:
    parts = []
    replaced = False
    for part in unquote_plus(connect_string).split(";"):
        key, separator, value = part.partition("=")
        if key.strip().lower() in {"database", "initial catalog"}:
            parts.append(f"{key}{separator}{database_name}")
            replaced = True
        elif part:
            parts.append(part)

    if not replaced:
        parts.append(f"DATABASE={database_name}")
    return ";".join(parts)


def _admin_url(url):
    odbc_connect = url.query.get("odbc_connect")
    if odbc_connect:
        return url.update_query_dict(
            {"odbc_connect": _odbc_connect_with_database(str(odbc_connect), "master")}
        )
    return url.set(database="master")


def wait_for_database() -> None:
    url = make_url(settings.DATABASE_URL)
    database_name = _validated_database_name(_database_name_from_url(url))
    admin_engine = create_engine(
        _admin_url(url),
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
        _alter_column_if_exists(connection, "users", "username", "NVARCHAR(50) NULL")
        _alter_column_if_exists(connection, "users", "password", "NVARCHAR(255) NULL")
        _alter_column_if_exists(connection, "users", "role", "NVARCHAR(20) NULL")
        _alter_column_if_exists(connection, "users", "name", "NVARCHAR(100) NULL")
        _add_column_if_missing(connection, "session_questions", "question_id", "NVARCHAR(64) NULL")
        _add_column_if_missing(connection, "session_questions", "used_hint", "INT NULL")
        _add_column_if_missing(connection, "game_data", "created_at", "DATETIME2 DEFAULT (GETUTCDATE()) NULL")
        # Drop FK from session_questions to sessions if exists
        connection.execute(text("""
            IF OBJECT_ID('FK__session_q__sessi__7E37BEF6') IS NOT NULL
                ALTER TABLE session_questions DROP CONSTRAINT FK__session_q__sessi__7E37BEF6;
        """))
        # Recreate sessions table to match ORM model ordering
        connection.execute(text("""
            IF OBJECT_ID('sessions', 'U') IS NOT NULL DROP TABLE sessions;
            CREATE TABLE sessions (
                session_id UNIQUEIDENTIFIER PRIMARY KEY,
                user_id VARCHAR(128) NOT NULL,
                game_id UNIQUEIDENTIFIER NOT NULL,
                start_time DATETIME2 NOT NULL DEFAULT (GETUTCDATE()),
                end_time DATETIME2 NULL,
                state VARCHAR(30) NOT NULL DEFAULT ('playing'),
                score INT NOT NULL DEFAULT (0),
                level INT NOT NULL DEFAULT (1),
                emotion_errors NVARCHAR(MAX) NULL,
                max_errors INT NOT NULL DEFAULT (3),
                level_threshold FLOAT NOT NULL DEFAULT (70.0),
                ratio NVARCHAR(MAX) NULL,
                time_limit INT NULL,
                question_ids NVARCHAR(MAX) NULL,
                CONSTRAINT FK_sessions_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                CONSTRAINT FK_sessions_game FOREIGN KEY (game_id) REFERENCES games(game_id) ON DELETE CASCADE
            );
        """))
        _alter_column_if_exists(connection, "games", "difficulty_level", "NVARCHAR(50) NULL")
        _alter_column_if_exists(connection, "game_content", "content_type", "NVARCHAR(50) NOT NULL")
        _alter_column_if_exists(connection, "game_content", "media_path", "NVARCHAR(500) NULL")
        _alter_column_if_exists(connection, "game_content", "question_text", "NVARCHAR(MAX) NULL")
        _alter_column_if_exists(connection, "game_content", "correct_answer", "NVARCHAR(100) NULL")
        _alter_column_if_exists(connection, "game_content", "emotion", "NVARCHAR(100) NULL")
        _alter_column_if_exists(connection, "game_content", "explanation", "NVARCHAR(MAX) NULL")
        _alter_column_if_exists(connection, "children", "report_preferences", "NVARCHAR(512) NULL")
        _alter_column_if_exists(connection, "emotion_concepts", "emotion", "NVARCHAR(100) NOT NULL")
        _alter_column_if_exists(connection, "emotion_concepts", "title", "NVARCHAR(255) NOT NULL")
        _alter_column_if_exists(connection, "emotion_concepts", "video_path", "NVARCHAR(500) NULL")
        _alter_column_if_exists(connection, "emotion_concepts", "image_path", "NVARCHAR(500) NULL")
        _alter_column_if_exists(connection, "emotion_concepts", "audio_path", "NVARCHAR(500) NULL")
        # Drop old FK referencing 'questions' table if it exists and add correct FK to game_content
        connection.execute(text("""
            DECLARE @fk_name NVARCHAR(128);
            SELECT @fk_name = name FROM sys.foreign_keys
            WHERE parent_object_id = OBJECT_ID('game_data_question')
              AND referenced_object_id = OBJECT_ID('game_content');
            IF @fk_name IS NULL
            BEGIN
                ALTER TABLE game_data_question ADD CONSTRAINT FK_game_data_question_content_id FOREIGN KEY (question_id) REFERENCES game_content(content_id);
            END
        """))
        # Backfill legacy rows so progress payload is always JSON-shaped for FE.
        connection.execute(
            text(
                """
                UPDATE child_progress
                SET ratio='[0.1667,0.1667,0.1667,0.1667,0.1667,0.1665]'
                WHERE ratio IS NULL OR LTRIM(RTRIM(ratio))=''
                """
            )
        )
        connection.execute(
            text(
                """
                UPDATE child_progress
                SET review_emotions=:review_emotions
                WHERE review_emotions IS NULL
                  OR LTRIM(RTRIM(review_emotions))=''
                  OR LTRIM(RTRIM(review_emotions))='[]'
                """
            ),
            {"review_emotions": json.dumps({"happy": 0, "sad": 0, "angry": 0, "fear": 0, "surprise": 0, "disgust": 0})},
        )


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
