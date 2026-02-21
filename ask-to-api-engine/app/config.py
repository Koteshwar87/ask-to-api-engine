from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    openai_api_key: str = Field(min_length=20)
    chat_model: str = "gpt-4o-mini"
    embedding_model: str = "text-embedding-3-small"
    swagger_specs_dir: str = "swagger_specs"
    database_url: str = "postgresql+psycopg://postgres:postgres@localhost:5432/ask_to_api"
    cors_origins: str = "http://localhost:3000,http://localhost:5173,http://localhost:8000"

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}

    @property
    def specs_path(self) -> Path:
        return Path(self.swagger_specs_dir)
