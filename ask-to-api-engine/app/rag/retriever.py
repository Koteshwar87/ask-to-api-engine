import logging

from langchain_postgres import PGVector

from app.swagger.catalog import SwaggerCatalog
from app.swagger.models import ApiOperationDescriptor

logger = logging.getLogger(__name__)


DEFAULT_SCORE_THRESHOLD = 1.2


def retrieve_operations(
    query: str,
    vector_store: PGVector,
    catalog: SwaggerCatalog,
    top_k: int = 5,
    score_threshold: float = DEFAULT_SCORE_THRESHOLD,
) -> list[ApiOperationDescriptor]:
    """Run similarity search and map results back to ApiOperationDescriptor via catalog."""
    docs_with_scores = vector_store.similarity_search_with_score(query, k=top_k)

    results: list[ApiOperationDescriptor] = []
    seen: set[str] = set()

    for doc, score in docs_with_scores:
        if score > score_threshold:
            logger.debug("Skipping doc (score %.3f > threshold %.3f): %s", score, score_threshold, doc.metadata.get("operationId"))
            continue

        op_id = doc.metadata.get("operationId")
        if not op_id or op_id in seen:
            continue
        seen.add(op_id)

        op = catalog.find_by_id(op_id)
        if op:
            results.append(op)

    logger.debug("Retrieved %d operations for query: %s", len(results), query)
    return results
