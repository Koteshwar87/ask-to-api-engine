import logging

from langchain_chroma import Chroma

from app.swagger.catalog import SwaggerCatalog
from app.swagger.models import ApiOperationDescriptor

logger = logging.getLogger(__name__)


def retrieve_operations(
    query: str,
    vector_store: Chroma,
    catalog: SwaggerCatalog,
    top_k: int = 5,
) -> list[ApiOperationDescriptor]:
    """Run similarity search and map results back to ApiOperationDescriptor via catalog."""
    docs = vector_store.similarity_search(query, k=top_k)

    results: list[ApiOperationDescriptor] = []
    seen: set[str] = set()

    for doc in docs:
        op_id = doc.metadata.get("operationId")
        if not op_id or op_id in seen:
            continue
        seen.add(op_id)

        op = catalog.find_by_id(op_id)
        if op:
            results.append(op)

    logger.debug("Retrieved %d operations for query: %s", len(results), query)
    return results
