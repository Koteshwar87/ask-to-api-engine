package com.asktoapiengine.engine.ai.browse.api;

import lombok.Data;

/**
 * Simple request payload for /ai/browse.
 *
 * The SPA or Postman will send:
 * {
 *     "query": "your natural language question"
 * }
 *
 * We intentionally keep this minimal.
 */
@Data
public class BrowseRequest {

    /**
     * Natural language question from the user.
     */
    private String query;
}
