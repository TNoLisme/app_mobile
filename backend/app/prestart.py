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
    try:
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
    except Exception as e:
        print(f"Warning: Could not alter column {table_name}.{column_name}: {e}")


def apply_additive_migrations() -> None:
    with engine.connect().execution_options(isolation_level="AUTOCOMMIT") as connection:
        _add_column_if_missing(connection, "users", "password", "NVARCHAR(255) NULL")
        _alter_column_if_exists(connection, "users", "username", "NVARCHAR(50) NULL")
        _alter_column_if_exists(connection, "users", "password", "NVARCHAR(255) NULL")
        _alter_column_if_exists(connection, "users", "role", "NVARCHAR(20) NULL")
        _alter_column_if_exists(connection, "users", "name", "NVARCHAR(100) NULL")
        _add_column_if_missing(
            connection,
            "children",
            "created_at",
            "DATETIME2 DEFAULT (GETUTCDATE()) NULL",
        )
        _add_column_if_missing(connection, "children", "last_login", "DATETIME2 NULL")
        _add_column_if_missing(connection, "children", "last_played", "DATETIME2 NULL")
        _add_column_if_missing(connection, "session_questions", "question_id", "NVARCHAR(64) NULL")
        _add_column_if_missing(connection, "session_questions", "used_hint", "INT NULL")
        _add_column_if_missing(connection, "game_data", "created_at", "DATETIME2 DEFAULT (GETUTCDATE()) NULL")
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
        # Older databases linked question_id directly to game_content. The
        # current domain model creates Question rows first, so keep the join
        # table aligned with questions.question_id.
        connection.execute(text("""
            DECLARE @legacy_fk_name NVARCHAR(128);

            SELECT TOP 1 @legacy_fk_name = fk.name
            FROM sys.foreign_keys fk
            JOIN sys.foreign_key_columns fkc
              ON fkc.constraint_object_id = fk.object_id
            WHERE fk.parent_object_id = OBJECT_ID('game_data_question')
              AND COL_NAME(fkc.parent_object_id, fkc.parent_column_id) = 'question_id'
              AND fk.referenced_object_id <> OBJECT_ID('questions');

            IF @legacy_fk_name IS NOT NULL
            BEGIN
                DECLARE @drop_fk_sql NVARCHAR(MAX);
                SET @drop_fk_sql = N'ALTER TABLE game_data_question DROP CONSTRAINT '
                    + QUOTENAME(@legacy_fk_name) + N';';
                EXEC sp_executesql @drop_fk_sql;
            END;

            DELETE link
            FROM game_data_question AS link
            LEFT JOIN questions AS question
              ON question.question_id = link.question_id
            WHERE question.question_id IS NULL;

            IF NOT EXISTS (
                SELECT 1
                FROM sys.foreign_keys fk
                JOIN sys.foreign_key_columns fkc
                  ON fkc.constraint_object_id = fk.object_id
                WHERE fk.parent_object_id = OBJECT_ID('game_data_question')
                  AND COL_NAME(fkc.parent_object_id, fkc.parent_column_id) = 'question_id'
                  AND fk.referenced_object_id = OBJECT_ID('questions')
            )
            BEGIN
                ALTER TABLE game_data_question
                ADD CONSTRAINT FK_game_data_question_question
                FOREIGN KEY (question_id) REFERENCES questions(question_id);
            END;
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
        # Normalize legacy click-game scores from the old 10-points-per-answer
        # scale to the shared 0-100 scale. Recompute from answers so this stays
        # safe to run on every startup.
        connection.execute(
            text(
                """
                IF OBJECT_ID('sessions') IS NOT NULL
                   AND OBJECT_ID('session_questions') IS NOT NULL
                   AND OBJECT_ID('games') IS NOT NULL
                BEGIN
                    ;WITH click_scores AS (
                        SELECT
                            s.session_id,
                            CAST(ROUND(
                                SUM(CASE WHEN sq.is_correct = 1 THEN 1 ELSE 0 END) * 100.0
                                / NULLIF(COUNT(sq.id), 0),
                                0
                            ) AS INT) AS normalized_score
                        FROM sessions AS s
                        JOIN games AS g
                          ON g.game_id = s.game_id
                        JOIN session_questions AS sq
                          ON sq.session_id = s.session_id
                        WHERE g.game_type = 'click_game'
                          AND s.end_time IS NOT NULL
                        GROUP BY s.session_id
                    )
                    UPDATE s
                    SET s.score = cs.normalized_score
                    FROM sessions AS s
                    JOIN click_scores AS cs
                      ON cs.session_id = s.session_id;
                END
                """
            )
        )
        connection.execute(
            text(
                """
                IF OBJECT_ID('child_progress') IS NOT NULL
                   AND OBJECT_ID('sessions') IS NOT NULL
                   AND OBJECT_ID('games') IS NOT NULL
                BEGIN
                    ;WITH latest_click_session AS (
                        SELECT
                            s.user_id,
                            s.game_id,
                            s.score,
                            ROW_NUMBER() OVER (
                                PARTITION BY s.user_id, s.game_id
                                ORDER BY s.end_time DESC, s.start_time DESC
                            ) AS rn
                        FROM sessions AS s
                        JOIN games AS g
                          ON g.game_id = s.game_id
                        WHERE g.game_type = 'click_game'
                          AND s.end_time IS NOT NULL
                          AND s.score IS NOT NULL
                    )
                    UPDATE cp
                    SET cp.score = latest.score
                    FROM child_progress AS cp
                    JOIN latest_click_session AS latest
                      ON latest.user_id = cp.child_id
                     AND latest.game_id = cp.game_id
                     AND latest.rn = 1;
                END
                """
            )
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
