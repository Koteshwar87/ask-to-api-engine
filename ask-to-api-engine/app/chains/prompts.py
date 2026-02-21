from langchain_core.prompts import ChatPromptTemplate

from app.swagger.models import ApiOperationDescriptor, ApiParameterDescriptor, ApiParameterLocation

BROWSE_PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "You are an expert API assistant for a financial data platform.\n"
            "Your job is to help the user understand WHICH HTTP API endpoint to call, "
            "and HOW to call it (method, path, path params, query params, and request body if any).\n"
            "You MUST only answer using the API operations listed in the context.\n"
            "If none of the operations are a good match, say that clearly.",
        ),
        (
            "human",
            'User question:\n"{query}"\n\n'
            "Here are the candidate API operations:\n\n{context}\n\n"
            "Based on the user's question and the operations above, "
            "explain in clear English which endpoint(s) the user should call.\n"
            "For each recommended endpoint, include:\n"
            "  - HTTP method and full path\n"
            "  - Path parameters with example values\n"
            "  - Query parameters with example values\n"
            "  - Whether a JSON request body is required (and a rough JSON example if applicable)\n"
            "  - A short explanation of what the endpoint returns\n\n"
            "Format your response as clear bullet points and short paragraphs. "
            "Do NOT invent endpoints that are not listed above.",
        ),
    ]
)


def format_operations_context(operations: list[ApiOperationDescriptor]) -> str:
    """Format a list of operations into the context string for the prompt."""
    if not operations:
        return "NO_OPERATIONS_AVAILABLE"

    parts: list[str] = []
    for i, op in enumerate(operations, 1):
        lines: list[str] = []
        lines.append(f"{i}) ID: {op.id}")
        lines.append(f"   Method: {op.http_method}")
        lines.append(f"   Path: {op.path}")

        if op.summary:
            lines.append(f"   Summary: {op.summary}")
        if op.description:
            lines.append(f"   Description: {op.description}")
        if op.tags:
            lines.append(f"   Tags: {', '.join(op.tags)}")

        path_params = [p for p in op.parameters if p.location == ApiParameterLocation.PATH]
        query_params = [p for p in op.parameters if p.location == ApiParameterLocation.QUERY]

        if path_params:
            lines.append("   Path parameters:")
            for p in path_params:
                lines.append(_format_param(p))

        if query_params:
            lines.append("   Query parameters:")
            for p in query_params:
                lines.append(_format_param(p))

        if op.has_request_body:
            body = "   Request body: YES"
            if op.request_body_summary:
                body += f" - {op.request_body_summary}"
            lines.append(body)
        else:
            lines.append("   Request body: NO")

        if op.source_name:
            lines.append(f"   Source: {op.source_name}")

        parts.append("\n".join(lines))

    return "\n\n".join(parts)


def _format_param(p: ApiParameterDescriptor) -> str:
    req = "required" if p.required else "optional"
    line = f"      - {p.name} [{req}]"
    if p.type:
        line += f" (type: {p.type})"
    if p.description:
        line += f" - {p.description}"
    if p.example:
        line += f" (example: {p.example})"
    return line
