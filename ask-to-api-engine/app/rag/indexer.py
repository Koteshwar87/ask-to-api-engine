import logging

from langchain_core.documents import Document
from langchain_chroma import Chroma

from app.swagger.models import ApiOperationDescriptor

logger = logging.getLogger(__name__)


def index_operations(
    operations: list[ApiOperationDescriptor], vector_store: Chroma
) -> None:
    """Convert operations to LangChain Documents and add them to the vector store."""
    documents = [_to_document(op) for op in operations]
    vector_store.add_documents(documents)
    logger.info("Indexed %d operations into ChromaDB", len(documents))


def _to_document(op: ApiOperationDescriptor) -> Document:
    content = _build_content(op)
    metadata = {
        "operationId": op.id,
        "httpMethod": op.http_method,
        "path": op.path,
    }
    if op.source_name:
        metadata["sourceName"] = op.source_name
    if op.tags:
        metadata["tags"] = ", ".join(op.tags)
    return Document(page_content=content, metadata=metadata)


def _build_content(op: ApiOperationDescriptor) -> str:
    lines: list[str] = []

    lines.append(f"[{op.http_method}] {op.path}")

    if op.summary:
        lines.append(f"Summary: {op.summary}")
    if op.description:
        lines.append(f"Description: {op.description}")
    if op.tags:
        lines.append(f"Tags: {', '.join(op.tags)}")

    if op.parameters:
        lines.append("Parameters:")
        for p in op.parameters:
            req = "required" if p.required else "optional"
            parts = [f"  - {p.name} ({p.location}) [{req}]"]
            if p.type:
                parts.append(f"type={p.type}")
            if p.description:
                parts.append(f"- {p.description}")
            if p.example:
                parts.append(f"(example: {p.example})")
            lines.append(" ".join(parts))

    if op.has_request_body:
        body_line = "Request Body: present"
        if op.request_body_summary:
            body_line += f" - {op.request_body_summary}"
        lines.append(body_line)

    if op.source_name:
        lines.append(f"Source: {op.source_name}")

    return "\n".join(lines)
