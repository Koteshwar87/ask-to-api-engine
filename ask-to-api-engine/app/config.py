from pathlib import Path

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    openai_api_key: str
    chat_model: str = "gpt-4o-mini"
    embedding_model: str = "text-embedding-3-small"
    swagger_specs_dir: str = "swagger_specs"
    chroma_collection_name: str = "swagger_operations"

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}

    @property
    def specs_path(self) -> Path:
        return Path(self.swagger_specs_dir)
