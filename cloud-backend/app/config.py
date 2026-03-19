from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    DATABASE_URL: str = "postgresql+asyncpg://anti:anti@localhost:5432/anticheat"
    REDIS_URL: str = "redis://localhost:6379"
    ANTHROPIC_API_KEY: str = ""
    API_SECRET_KEY: str = "changeme"
    DEBUG: bool = False
    RATE_LIMIT_PER_MINUTE: int = 100
    CLAUDE_SONNET_MODEL: str = "claude-3-5-sonnet-20241022"
    CLAUDE_OPUS_MODEL: str = "claude-3-opus-20240229"
    AI_TIER3_THRESHOLD: float = 0.6
    CACHE_TTL_SECONDS: int = 300


settings = Settings()
