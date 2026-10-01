"""Configuration settings for Sahaayika backend."""

import os
from pathlib import Path
from pydantic import BaseModel, Field
from dotenv import load_dotenv

# Search for .env in current directory, backend directory, or project root
_possible_env_paths = [
    Path.cwd() / ".env",
    Path(__file__).resolve().parent.parent / ".env",
    Path(__file__).resolve().parent.parent.parent / ".env",
]

for _env_path in _possible_env_paths:
    if _env_path.exists():
        load_dotenv(dotenv_path=_env_path, override=False)
        break
else:
    load_dotenv(override=False)


class Settings(BaseModel):
    """Application settings with environment variable fallback."""

    GEMINI_API_KEY: str = Field(default_factory=lambda: os.getenv("GEMINI_API_KEY", ""))
    GEMINI_MODEL: str = Field(default_factory=lambda: os.getenv("GEMINI_MODEL", "gemini-2.5-flash-lite"))
    PORT: int = Field(default_factory=lambda: int(os.getenv("PORT", "8000")))
    HOST: str = Field(default_factory=lambda: os.getenv("HOST", "0.0.0.0"))
    DEFAULT_LANGUAGE: str = Field(default_factory=lambda: os.getenv("DEFAULT_LANGUAGE", "hi-IN"))

    model_config = {"extra": "ignore"}


settings = Settings()


def get_settings() -> Settings:
    """Return the global application settings instance."""
    return settings
