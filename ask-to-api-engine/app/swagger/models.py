from enum import StrEnum

from pydantic import BaseModel


class ApiParameterLocation(StrEnum):
    PATH = "path"
    QUERY = "query"
    HEADER = "header"
    COOKIE = "cookie"


class ApiParameterDescriptor(BaseModel):
    name: str
    location: ApiParameterLocation
    required: bool = False
    type: str | None = None
    description: str | None = None
    example: str | None = None


class ApiOperationDescriptor(BaseModel):
    id: str
    http_method: str
    path: str
    summary: str | None = None
    description: str | None = None
    tags: list[str] = []
    parameters: list[ApiParameterDescriptor] = []
    has_request_body: bool = False
    request_body_summary: str | None = None
    source_name: str | None = None
