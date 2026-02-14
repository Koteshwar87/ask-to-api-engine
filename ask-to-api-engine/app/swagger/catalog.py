from .models import ApiOperationDescriptor


class SwaggerCatalog:
    """In-memory catalog for looking up API operations by id, tag, or path."""

    def __init__(self, operations: list[ApiOperationDescriptor]) -> None:
        self._by_id: dict[str, ApiOperationDescriptor] = {
            op.id: op for op in operations
        }

    def find_by_id(self, operation_id: str) -> ApiOperationDescriptor | None:
        return self._by_id.get(operation_id)

    def find_by_tag(self, tag: str) -> list[ApiOperationDescriptor]:
        return [op for op in self._by_id.values() if tag in op.tags]

    def get_all(self) -> list[ApiOperationDescriptor]:
        return list(self._by_id.values())
