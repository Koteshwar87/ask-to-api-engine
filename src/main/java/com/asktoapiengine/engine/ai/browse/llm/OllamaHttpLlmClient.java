package com.asktoapiengine.engine.ai.browse.llm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * LLM client implementation that calls a local Ollama instance
 * using its OpenAI-compatible /v1/chat/completions endpoint.
 *
 * This adapter is active only when:
 *   llm.provider = OLLAMA_HTTP
 *
 * Typical local configuration (application.yaml):
 *   llm:
 *     provider: OLLAMA_HTTP
 *     http:
 *       base-url: http://localhost:11434/v1
 *       api-key: ollama   # dummy value, not used by Ollama
 *       model: llama3.1   # or any other model you pulled in Ollama
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "OLLAMA_HTTP")
@RequiredArgsConstructor
public class OllamaHttpLlmClient implements LlmClient {

    /**
     * WebClient configured in HttpLlmConfig.
     * For this adapter, it should point to the local Ollama base URL.
     */
    private final WebClient llmHttpWebClient;

    /**
     * Model name used for Ollama HTTP calls.
     * Driven by llm.http.model, with a default that matches a common Ollama model.
     */
    @Value("${llm.http.model:llama3.1}")
    private String modelName;

    /**
     * Sends the prompt to the local Ollama instance using the /chat/completions API
     * and returns the first answer text.
     *
     * @param prompt human-readable prompt prepared by the application
     * @return answer text returned by Ollama
     */
    @Override
    public String generate(String prompt) {
        log.info("OllamaHttpLlmClient: sending prompt to local Ollama using model={}", modelName);

        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "You are an AI assistant helping users browse and understand REST APIs."
                        ),
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                )
        );

        JsonNode response = llmHttpWebClient
                .post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(180)); // allow more time for local large models

        log.info("OllamaHttpLlmClient: received HTTP response from local Ollama");

        if (response == null) {
            log.info("OllamaHttpLlmClient: response body is null");
            return "Sorry, I could not process your request because the Ollama response was empty.";
        }

        JsonNode choicesNode = response.get("choices");
        if (choicesNode == null || !choicesNode.isArray() || choicesNode.isEmpty()) {
            log.info("OllamaHttpLlmClient: 'choices' field is missing or empty in Ollama response");
            return "Sorry, I could not process your request because Ollama returned no choices.";
        }

        JsonNode firstChoice = choicesNode.get(0);
        if (firstChoice == null) {
            log.info("OllamaHttpLlmClient: first element in 'choices' array is null");
            return "Sorry, I could not process your request because the Ollama response was invalid.";
        }

        JsonNode messageNode = firstChoice.get("message");
        if (messageNode == null) {
            log.info("OllamaHttpLlmClient: 'message' field is missing in first choice");
            return "Sorry, I could not process your request because the Ollama message field was missing.";
        }

        JsonNode contentNode = messageNode.get("content");
        if (contentNode == null || contentNode.isNull()) {
            log.info("OllamaHttpLlmClient: 'content' field is missing in message");
            return "Sorry, I could not process your request because the Ollama content field was missing.";
        }

        String content = contentNode.asText();
        log.info("OllamaHttpLlmClient: successfully extracted answer from Ollama response");

        return content;
    }
}
