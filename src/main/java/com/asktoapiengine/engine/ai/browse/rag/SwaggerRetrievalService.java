package com.asktoapiengine.engine.ai.browse.rag;

import com.asktoapiengine.engine.ai.browse.swagger.ApiOperationDescriptor;
import com.asktoapiengine.engine.ai.browse.swagger.SwaggerApiCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SwaggerRetrievalService performs semantic retrieval (R in RAG).
 *
 * Responsibilities:
 *  1. Accept a user query (natural language)
 *  2. Query the VectorStore using similarity search
 *  3. Retrieve top-K Documents representing relevant Swagger operations
 *  4. Convert those Documents back to ApiOperationDescriptor using operationId
 *  5. Return a deduplicated list of candidate operations for LLM prompt construction
 *
 * This service:
 *  - does NOT call LLMs directly
 *  - does NOT format final answers
 *  - It only retrieves the "most semantically relevant" APIs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SwaggerRetrievalService {

    private final VectorStore vectorStore;
    private final SwaggerApiCatalog catalog;

    /** Number of relevant Swagger operations to retrieve */
    private static final int DEFAULT_TOP_K = 5;

    /**
     * Main method used by BrowseService.
     *
     * @param query the user's natural language question
     * @return list of best candidate API operations
     */
    public List<ApiOperationDescriptor> retrieveRelevantOperations(String query) {
        log.info("SwaggerRetrievalService: searching operations for query='{}'", query);
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // 1. Perform vector search
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(DEFAULT_TOP_K)
                .build();
        log.debug("SwaggerRetrievalService: SearchRequest = {}", searchRequest);

        List<Document> docs = vectorStore.similaritySearch(searchRequest);

        List<ApiOperationDescriptor> results = new ArrayList<>();

        // 2. Convert each Document → ApiOperationDescriptor
        for (Document doc : docs) {
            String operationId = extractOperationId(doc);

            if (operationId == null) {
                continue;
            }

            // Look up in SwaggerApiCatalog
            Optional<ApiOperationDescriptor> op = catalog.findByOperationId(operationId);
            op.ifPresent(results::add);
        }
        log.info("SwaggerRetrievalService: found {} operations for query='{}'",
                results.size(), query);

        return results;
    }

    /**
     * Extract operationId from Document metadata.
     *
     * The metadata was set by SwaggerToDocumentMapper during indexing.
     */
    private String extractOperationId(Document doc) {
        if (doc == null || doc.getMetadata() == null) {
            return null;
        }

        Object value = doc.getMetadata().get("operationId");
        return (value != null) ? value.toString() : null;
    }
}
