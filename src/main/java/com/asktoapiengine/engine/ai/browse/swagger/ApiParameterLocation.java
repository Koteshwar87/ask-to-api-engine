package com.asktoapiengine.engine.ai.browse.swagger;

/**
 * Represents where a parameter appears in an API operation.
 *
 * Directly maps to OpenAPI "in" locations.
 */
public enum ApiParameterLocation {
    PATH,
    QUERY,
    HEADER,
    COOKIE
}