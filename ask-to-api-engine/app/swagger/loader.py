import json
import logging
from pathlib import Path

from .models import (
    ApiOperationDescriptor,
    ApiParameterDescriptor,
    ApiParameterLocation,
)

logger = logging.getLogger(__name__)

_HTTP_METHODS = ["get", "post", "put", "delete", "patch"]


def load_all_operations(specs_dir: Path) -> list[ApiOperationDescriptor]:
    """Load all OpenAPI 3.x JSON files from specs_dir and return a flat list of operations."""
    operations: list[ApiOperationDescriptor] = []

    for spec_file in sorted(specs_dir.glob("*.json")):
        try:
            with open(spec_file, encoding="utf-8") as f:
                spec = json.load(f)
        except (json.JSONDecodeError, OSError) as e:
            logger.error("Failed to load %s: %s", spec_file.name, e)
            continue

        paths = spec.get("paths", {})
        for path_template, path_item in paths.items():
            path_level_params = path_item.get("parameters", [])

            for method in _HTTP_METHODS:
                operation = path_item.get(method)
                if operation is None:
                    continue

                op = _build_operation(
                    path_template, method, operation, path_level_params, spec_file.name
                )
                operations.append(op)

    logger.info("Loaded %d operations from %s", len(operations), specs_dir)
    return operations


def _build_operation(
    path: str,
    method: str,
    operation: dict,
    path_level_params: list[dict],
    source_name: str,
) -> ApiOperationDescriptor:
    operation_id = operation.get("operationId") or f"{method.upper()} {path}"

    params = [_build_param(p) for p in path_level_params]
    params += [_build_param(p) for p in operation.get("parameters", [])]

    request_body = operation.get("requestBody")

    return ApiOperationDescriptor(
        id=operation_id,
        http_method=method.upper(),
        path=path,
        summary=operation.get("summary"),
        description=operation.get("description"),
        tags=operation.get("tags", []),
        parameters=params,
        has_request_body=request_body is not None,
        request_body_summary=request_body.get("description") if request_body else None,
        source_name=source_name,
    )


def _build_param(p: dict) -> ApiParameterDescriptor:
    schema = p.get("schema", {})
    schema_type = schema.get("type")
    schema_format = schema.get("format")

    if schema_type and schema_format:
        param_type = f"{schema_type}({schema_format})"
    else:
        param_type = schema_type

    example = p.get("example")

    return ApiParameterDescriptor(
        name=p.get("name", ""),
        location=_map_location(p.get("in")),
        required=p.get("required", False),
        type=param_type,
        description=p.get("description"),
        example=str(example) if example is not None else None,
    )


def _map_location(value: str | None) -> ApiParameterLocation:
    if value is None:
        return ApiParameterLocation.QUERY
    mapping = {
        "path": ApiParameterLocation.PATH,
        "query": ApiParameterLocation.QUERY,
        "header": ApiParameterLocation.HEADER,
        "cookie": ApiParameterLocation.COOKIE,
    }
    return mapping.get(value.lower(), ApiParameterLocation.QUERY)
