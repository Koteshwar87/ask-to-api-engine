package com.asktoapiengine.engine.ai.browse.llm;

import com.asktoapiengine.engine.ai.browse.swagger.ApiOperationDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * WebClient-based LLM service for the "Browse" use case.
 *
 * This uses the LlmClient abstraction, which is currently implemented
 * by OpenAiWebClientLlmClient (calling OpenAI's /chat/completions HTTP API).
 *
 * Existing BrowseLlmService (ChatModel-based) is left untouched so that
 * you can easily switch between them while experimenting.
 */
@Service
@RequiredArgsConstructor
public class BrowseWebClientLlmService {

    /**
     * Abstraction over any LLM provider (OpenAI, SparkAssist, etc.).
     * Current implementation: OpenAiWebClientLlmClient.
     */
    private final LlmClient llmClient;

    /**
     * Helper that knows how to build a good prompt for the "browse" use case.
     */
    private final BrowsePromptBuilder promptBuilder;

    /**
     * Main entry point for the Browse use case on the LLM side (WebClient path).
     *
     * @param userQuery           natural language question from the user
     * @param candidateOperations list of relevant API operations (from RAG retrieval)
     * @return plain-English answer describing which endpoint(s) to use and how
     */
    public String getBrowseAnswer(String userQuery, List<ApiOperationDescriptor> candidateOperations) {
        if (userQuery == null || userQuery.isBlank()) {
            return "I did not receive a question. Please provide a natural language query about the APIs.";
        }

        // Build the full prompt string using the query + candidate Swagger operations
        String prompt = promptBuilder.buildPrompt(userQuery, candidateOperations);

        // Delegate to the LLM via LlmClient (backed by WebClient + OpenAI HTTP API).
        try {
            return llmClient.generate(prompt);
        } catch (RuntimeException ex) {
            return "Sorry, I could not process your browse request via WebClient due to an internal error: "
                    + ex.getMessage();
        }
    }
}
