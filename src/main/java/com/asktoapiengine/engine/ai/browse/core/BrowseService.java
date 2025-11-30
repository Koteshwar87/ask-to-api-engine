package com.asktoapiengine.engine.ai.browse.core;

import com.asktoapiengine.engine.ai.browse.llm.BrowseLlmService;
import com.asktoapiengine.engine.ai.browse.llm.BrowseWebClientLlmService;
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
 *  1. Accept the user's natural language query.
 *  2. Use SwaggerRetrievalService (R in RAG) to get relevant Swagger operations.
 *  3. Use BrowseLlmService to ask the LLM to explain which endpoint(s) to use and how.
 *  4. Return a plain-English answer string to the REST controller.
 *
 * This service:
 *  - Does NOT know about HTTP details (that's the controller's job).
 *  - Does NOT talk to the vector store directly (RAG retrieval service handles that).
 *  - Does NOT build prompts itself (BrowsePromptBuilder handles that via BrowseLlmService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrowseService {

    private final SwaggerRetrievalService retrievalService;

    // Existing ChatModel-based LLM service (Spring AI)
    private final BrowseLlmService browseLlmService;

    // New WebClient-based LLM service (using LlmClient + OpenAI HTTP API)
    private final BrowseWebClientLlmService browseWebClientLlmService;

    /**
     * Main method to be called by the controller for /ai/browse.
     *
     * @param userQuery Natural language question about the APIs.
     * @return Plain-English answer describing the appropriate endpoints and how to call them.
     */
    public String handleBrowseQuery(String userQuery) {
        log.info("Handling browse query='{}'", userQuery);
        if (userQuery == null || userQuery.isBlank()) {
            return "Please provide a question about the APIs (for example: "
                    + "\"How do I get index levels for NIFTY 50 between two dates?\").";
        }

        // 1. Use RAG retrieval to get the most relevant Swagger operations
        List<ApiOperationDescriptor> candidateOperations =
                retrievalService.retrieveRelevantOperations(userQuery);

        log.info("BrowseService: retrieved {} candidate operations for query='{}'",
                candidateOperations.size(), userQuery);

        if (log.isDebugEnabled()) {
            candidateOperations.forEach(op ->
                    log.debug("Candidate op: {} {}", op.getHttpMethod(), op.getPath())
            );
        }

        // If we couldn't find anything meaningful in Swagger, return a graceful message.
        if (candidateOperations.isEmpty()) {
            return "I could not find any API endpoints in the documentation that match your question. "
                    + "Please try rephrasing your query or check if the API is documented.";
        }

        // 2. Ask the LLM to analyze these operations and explain the best endpoint(s) to use

        // OPTION 1: Use existing Spring AI ChatModel-based implementation
//        return browseLlmService.getBrowseAnswer(userQuery, candidateOperations);

        // OPTION 2: Use new WebClient-based implementation (OpenAI HTTP API)
        // To switch, comment the line above and uncomment the line below:
        log.info("BrowseService: delegating to WebClient-based LLM");
        String answer = browseWebClientLlmService.getBrowseAnswer(userQuery, candidateOperations);

        log.info("BrowseService: received answer of length={}", answer != null ? answer.length() : 0);

        return answer;
    }
}
