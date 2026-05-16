from typing import Optional
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    DATABASE_URL: str
    SECRET_KEY: str

    # Optional email config for outgoing reports
    EMAIL_USER: Optional[str] = None
    EMAIL_PASS: Optional[str] = None

    class Config:
        env_file = ".env"


settings = Settings()