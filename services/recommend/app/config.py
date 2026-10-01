import os
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    APP_NAME: str = "TicketBooking AI Recommendation Service"
    PORT: int = 8088
    DEBUG: bool = False

    # OpenAI Configuration
    OPENAI_API_KEY: str = os.getenv("OPENAI_API_KEY", "")
    OPENAI_MODEL: str = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    OPENAI_EMBEDDING_MODEL: str = os.getenv("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small")

    # Internal Microservice Connections
    CATALOG_SERVICE_URL: str = os.getenv("CATALOG_SERVICE_URL", "http://localhost:8083")

    # Cache TTL (seconds)
    CACHE_TTL_SECONDS: int = 300

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()
