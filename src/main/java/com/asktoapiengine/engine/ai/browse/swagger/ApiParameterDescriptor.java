package com.asktoapiengine.engine.ai.browse.swagger;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes a single parameter of an API operation.
 *
 * Lombok annotations used:
 * - @Data:        Generates getters, setters, toString, equals/hashCode
 * - @NoArgsConstructor: Default constructor
 * - @AllArgsConstructor: All-args constructor
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiParameterDescriptor {

    /** Parameter name (e.g., "indexId", "fromDate") */
    private String name;

    /** Where this parameter appears in the API path/query/header/cookie */
    private ApiParameterLocation location;

    /** Whether this parameter is mandatory */
    private boolean required;

    /** Simplified data type from OpenAPI schema (string, integer, date, etc.) */
    private String type;

    /** Human-readable description from Swagger */
    private String description;

    /** Example value from Swagger (used for creating curl examples) */
    private String example;
}
