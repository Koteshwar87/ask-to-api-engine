package com.asktoapiengine.engine.ai.browse.swagger;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single API operation (one endpoint + one HTTP method).
 *
 * This is the core model that:
 * - SwaggerLoader populates
 * - SwaggerApiCatalog stores
 * - RAG indexing uses
 * - LLM uses to generate English explanations
 *
 * Lombok annotations:
 * - @Data: all getters/setters/toString/equals/hashCode
 * - @NoArgsConstructor: default constructor
 * - @AllArgsConstructor: full constructor
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiOperationDescriptor {

    /** Unique ID (preferably OpenAPI operationId; otherwise synthesized) */
    private String id;

    /** HTTP method in upper-case (GET, POST, PUT…) */
    private String httpMethod;

    /** Path template (e.g., "/indices/{indexId}/levels") */
    private String path;

    /** Short summary from Swagger */
    private String summary;

    /** Longer description from Swagger (if available) */
    private String description;

    /** Tags used for grouping API operations */
    private List<String> tags = new ArrayList<>();

    /** List of parameters (path/query/header/cookie) */
    private List<ApiParameterDescriptor> parameters = new ArrayList<>();

    /** Whether the operation expects a request body */
    private boolean hasRequestBody;

    /** Optional short summary of the request body schema */
    private String requestBodySummary;

    /** Name of the Swagger/YAML file this operation came from */
    private String sourceName;
}
