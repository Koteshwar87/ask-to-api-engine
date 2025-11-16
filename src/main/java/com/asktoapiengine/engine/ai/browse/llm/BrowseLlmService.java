package com.asktoapiengine.engine.ai.browse.llm;

import com.asktoapiengine.engine.ai.browse.swagger.ApiOperationDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * BrowseLlmService is the thin layer that talks to the LLM
 * using Spring AI's ChatModel.
 *
 * Responsibilities:
 *  - Take the user query and a small list of candidate API operations.
 *  - Use BrowsePromptBuilder to create a prompt string.
 *  - Call the ChatModel with that prompt.
 *  - Return the final plain-English answer as a String.
 *
 * NOTE:
 *  - This class does NOT do vector search (RAG retrieval).
 *    That is handled by SwaggerRetrievalService.
 *  - This class does NOT load Swagger or know about JSON files.
 *    It only consumes ApiOperationDescriptor objects.
 */
@Service
@RequiredArgsConstructor
public class BrowseLlmService {

    /**
     * Spring AI abstraction for chat-based models.
     *
     * With the OpenAI starter on the classpath and properties configured,
     * Spring Boot will auto-create an implementation (e.g., OpenAiChatModel).
     */
    private final ChatModel chatModel;

    /**
     * Helper that knows how to build a good prompt for the "browse" use case.
     */
    private final BrowsePromptBuilder promptBuilder;

    /**
     * Main entry point for the Browse use case on the LLM side.
     *
     * @param userQuery           natural language question from the user
     * @param candidateOperations list of relevant API operations (from RAG retrieval)
     * @return plain-English answer describing which endpoint(s) to call and how
     */
    public String getBrowseAnswer(String userQuery, List<ApiOperationDescriptor> candidateOperations) {
        if (userQuery == null || userQuery.isBlank()) {
            return "I did not receive a question. Please provide a natural language query about the APIs.";
        }

        // Build the full prompt string using the query + candidate Swagger operations
        String prompt = promptBuilder.buildPrompt(userQuery, candidateOperations);

        // Use Spring AI's ChatModel. The latest API allows a simple call(String)
        // which internally wraps this into a Prompt + ChatResponse.
        //
        // This returns just the model's textual answer.
        try {
            return chatModel.call(prompt);
        } catch (RuntimeException ex) {
            // In a real production app, you would log this properly and perhaps
            // map it to a user-friendly error message.
            //
            // For now, we return a simple message so you can see something useful
            // during development if the model call fails.
            return "Sorry, I could not process your browse request due to an internal error: "
                    + ex.getMessage();
        }
    }
}
