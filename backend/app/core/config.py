from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


BACKEND_DIR = Path(__file__).resolve().parents[2]

class Settings(BaseSettings):
    DATABASE_URL: str
    SECRET_KEY: str
    SMTP_HOST: str | None = "smtp.gmail.com"
    SMTP_PORT: int = 587
    SMTP_USERNAME: str | None = None
    SMTP_PASSWORD: str | None = None
    SMTP_USE_TLS: bool = True
    SMTP_FROM_EMAIL: str | None = None
    SMTP_FROM_NAME: str = "EmoGarden"
    EMAIL_USER: str | None = None
    EMAIL_PASS: str | None = None
    BEEKNOEE_API_KEY: str | None = None
    BEEKNOEE_BASE_URL: str = "https://platform.beeknoee.com/api/v1"
    MODEL: str = "gemini-2.5-flash"

    model_config = SettingsConfigDict(
        env_file=(BACKEND_DIR / ".env",),
        extra="ignore",
    )

settings = Settings()
