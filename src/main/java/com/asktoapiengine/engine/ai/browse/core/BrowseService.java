package com.asktoapiengine.engine.ai.browse.core;

import com.asktoapiengine.engine.ai.browse.llm.BrowseLlmService;
import com.asktoapiengine.engine.ai.browse.rag.SwaggerRetrievalService;
import com.asktoapiengine.engine.ai.browse.swagger.ApiOperationDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * BrowseService orchestrates the full "Browse APIs" use case.
 *
 * Responsibilities:
 *  1. Accept the user's natural language query from the controller.
 *  2. Use SwaggerRetrievalService (R in RAG) to find relevant Swagger operations.
 *  3. Delegate to BrowseLlmService to ask the LLM which endpoint(s) to use and how.
 *  4. Return a plain-English answer back to the controller.
 *
 * This service does not know which LLM provider is used. It only talks to
 * BrowseLlmService, which itself uses LlmClient. The concrete provider is
 * selected by the llm.provider property.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrowseService {

    /**
     * RAG retrieval component that finds the most relevant Swagger operations
     * for the user's query using the vector store.
     */
    private final SwaggerRetrievalService retrievalService;

    /**
     * LLM-facing service that builds the browse prompt and calls the LLM
     * through the LlmClient abstraction.
     */
    private final BrowseLlmService browseLlmService;

    /**
     * Main method invoked by the browse controller.
     *
     * @param userQuery natural language question about the APIs
     * @return plain-English answer explaining which endpoints to call and how
     */
    public String handleBrowseQuery(String userQuery) {
        log.info("BrowseService: handling browse query='{}'", userQuery);

        if (userQuery == null || userQuery.isBlank()) {
            log.info("BrowseService: received empty or blank query");
            return "Please provide a question about the APIs, for example: "
                    + "\"How do I get index levels for NIFTY 50 between two dates?\"";
        }

        // 1. Use RAG retrieval to find relevant operations from Swagger
        List<ApiOperationDescriptor> candidateOperations =
                retrievalService.retrieveRelevantOperations(userQuery);

        log.info("BrowseService: retrieved {} candidate operations for query='{}'",
                candidateOperations.size(), userQuery);

        if (candidateOperations.isEmpty()) {
            log.info("BrowseService: no candidate operations found for query");
            return "I could not find any API endpoints in the documentation that match your question. "
                    + "Please try rephrasing your query or check if the API is documented.";
        }

        // 2. Delegate to the LLM browse service
        log.info("BrowseService: delegating to BrowseLlmService for LLM answer");
        String answer = browseLlmService.getBrowseAnswer(userQuery, candidateOperations);

        log.info("BrowseService: received answer of length={}",
                answer != null ? answer.length() : 0);

        return answer;
    }
}
