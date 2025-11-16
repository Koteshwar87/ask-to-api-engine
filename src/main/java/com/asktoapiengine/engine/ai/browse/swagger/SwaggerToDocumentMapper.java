package com.asktoapiengine.engine.ai.browse.swagger;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SwaggerToDocumentMapper converts ApiOperationDescriptor objects
 * into Spring AI Document objects that can be stored in a VectorStore.
 *
 * Each Document contains:
 *  - content: a textual description of the API endpoint
 *  - metadata: structured fields like operationId, httpMethod, path, tags, sourceName
 *
 * This is the "bridge" between your Swagger world and the RAG world.
 *
 * NOTE:
 *  - This class does NOT talk to the vector store.
 *  - It does NOT call LLMs.
 *  - It is just a mapper used by SwaggerDocumentIndexer.
 */
@Component
public class SwaggerToDocumentMapper {

    /**
     * Convert a single ApiOperationDescriptor into a Spring AI Document.
     *
     * @param op the API operation descriptor
     * @return a Document representing this operation, ready for embeddings
     */
    public Document toDocument(ApiOperationDescriptor op) {
        if (op == null) {
            return null;
        }

        // 1. Build the "content" text for this document.
        //    This is what the embedding model will "read" and index.
        String content = buildContentText(op);

        // 2. Build metadata so we can map back from Documents to operations later.
        Map<String, Object> metadata = buildMetadata(op);

        return new Document(content, metadata);
    }

    /**
     * Convenience method to map a list of ApiOperationDescriptor objects
     * into a list of Documents.
     */
    public List<Document> toDocuments(List<ApiOperationDescriptor> operations) {
        return operations.stream()
                .map(this::toDocument)
                .filter(doc -> doc != null)
                .toList();
    }

    /**
     * Builds a human-readable description for the operation.
     *
     * This content is used for:
     *  - generating embeddings
     *  - giving context to the LLM when answering browse queries
     *
     * We try to include all relevant fields:
     *  method, path, summary, description, tags, parameters, request body.
     */
    private String buildContentText(ApiOperationDescriptor op) {
        StringBuilder sb = new StringBuilder();

        // Header line: [GET] /indices/{indexId}/levels
        sb.append("[").append(nullSafeUpper(op.getHttpMethod())).append("] ")
                .append(nullSafe(op.getPath())).append("\n");

        // Summary
        if (op.getSummary() != null && !op.getSummary().isBlank()) {
            sb.append("Summary: ").append(op.getSummary()).append("\n");
        }

        // Description
        if (op.getDescription() != null && !op.getDescription().isBlank()) {
            sb.append("Description: ").append(op.getDescription()).append("\n");
        }

        // Tags
        if (op.getTags() != null && !op.getTags().isEmpty()) {
            sb.append("Tags: ").append(String.join(", ", op.getTags())).append("\n");
        }

        // Parameters
        if (op.getParameters() != null && !op.getParameters().isEmpty()) {
            sb.append("Parameters:\n");
            op.getParameters().forEach(param -> {
                sb.append("  - ")
                        .append(param.getName())
                        .append(" (").append(param.getLocation()).append(")");

                if (param.isRequired()) {
                    sb.append(" [required]");
                } else {
                    sb.append(" [optional]");
                }

                if (param.getType() != null) {
                    sb.append(" type=").append(param.getType());
                }

                if (param.getDescription() != null && !param.getDescription().isBlank()) {
                    sb.append(" - ").append(param.getDescription());
                }

                if (param.getExample() != null && !param.getExample().isBlank()) {
                    sb.append(" (example: ").append(param.getExample()).append(")");
                }

                sb.append("\n");
            });
        }

        // Request body
        if (op.isHasRequestBody()) {
            sb.append("Request Body: present");
            if (op.getRequestBodySummary() != null && !op.getRequestBodySummary().isBlank()) {
                sb.append(" - ").append(op.getRequestBodySummary());
            }
            sb.append("\n");
        }

        // Source information
        if (op.getSourceName() != null && !op.getSourceName().isBlank()) {
            sb.append("Source: ").append(op.getSourceName()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Build metadata map for the Document.
     *
     * Metadata is NOT used for embeddings, but is very useful for:
     *  - filtering / searching by tags, method, path
     *  - mapping back from Document to ApiOperationDescriptor
     */
    private Map<String, Object> buildMetadata(ApiOperationDescriptor op) {
        Map<String, Object> metadata = new HashMap<>();

        // These keys are important for later:
        metadata.put("operationId", op.getId());
        metadata.put("httpMethod", nullSafeUpper(op.getHttpMethod()));
        metadata.put("path", nullSafe(op.getPath()));

        if (op.getSourceName() != null) {
            metadata.put("sourceName", op.getSourceName());
        }

        if (op.getTags() != null && !op.getTags().isEmpty()) {
            metadata.put("tags", op.getTags());
        }

        // You can add more meta fields later if needed
        return metadata;
    }

    private String nullSafe(String value) {
        return (value != null) ? value : "";
    }

    private String nullSafeUpper(String value) {
        return (value != null) ? value.toUpperCase() : "";
    }
}
