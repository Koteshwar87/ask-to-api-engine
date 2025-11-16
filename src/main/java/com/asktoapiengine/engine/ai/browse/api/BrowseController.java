package com.asktoapiengine.engine.ai.browse.api;

import com.asktoapiengine.engine.ai.browse.core.BrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the Browse API use case.
 *
 * Endpoint:
 *     POST /ai/browse
 *
 * Request:
 *     { "query": "..." }
 *
 * Response:
 *     Plain text (String) with the LLM's explanation of which API endpoint
 *     the user should call and how to call it.
 *
 * This controller does NOT do:
 *  - Retrieval (that's in SwaggerRetrievalService)
 *  - Prompt building (BrowsePromptBuilder)
 *  - Calling LLM (BrowseLlmService)
 *  - API operation selection logic (BrowseService)
 *
 * It only delegates to BrowseService and returns the final answer.
 */
@RestController
@RequestMapping("/ai/browse")
@RequiredArgsConstructor
public class BrowseController {

    private final BrowseService browseService;

    /**
     * POST /ai/browse
     *
     * Accepts a natural language query and returns the LLM response.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String browse(@RequestBody BrowseRequest request) {

        String query = request.getQuery();

        // Delegate to the core Browse service
        return browseService.handleBrowseQuery(query);
    }
}
