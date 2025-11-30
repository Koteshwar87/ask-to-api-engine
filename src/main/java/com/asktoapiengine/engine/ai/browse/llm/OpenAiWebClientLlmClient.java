package com.asktoapiengine.engine.ai.browse.llm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * WebClient-based implementation of LlmClient that calls OpenAI's
 * /chat/completions HTTP API.
 *
 * This is written in a blocking style (using block()) so that it can
 * be easily integrated into the existing imperative Spring MVC codebase.
 *
 * Later, when you switch to SparkAssist, you can add another implementation
 * of LlmClient that calls the SparkAssist endpoint instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiWebClientLlmClient implements LlmClient {

    private final WebClient openAiWebClient;

    /**
     * Model name is externalized so we can change it via configuration.
     * Example value: gpt-4o-mini
     */
    @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
    private String modelName;

    @Override
    public String generate(String prompt) {
        log.info("OpenAiWebClientLlmClient: sending request to OpenAI model='{}'", modelName);
        if (log.isDebugEnabled()) {
            log.debug("OpenAiWebClientLlmClient: prompt length={}", prompt != null ? prompt.length() : 0);
        }
        // Build request body using Map so it is easy to extend later.
        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                )
        );

        JsonNode response = openAiWebClient
                .post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(); // ok here, since we are in a non-reactive stack
        log.info("OpenAiWebClientLlmClient: received response from OpenAI");

        if (log.isDebugEnabled()) {
            log.debug("OpenAiWebClientLlmClient: raw response = {}", response);
        }

        // Safely navigate the response JSON:
        // choices[0].message.content
        if (response == null) {
            return "Sorry, I could not process your request (empty response from model).";
        }

        JsonNode choicesNode = response.get("choices");
        if (choicesNode == null || !choicesNode.isArray() || choicesNode.isEmpty()) {
            return "Sorry, I could not process your request (no choices returned by model).";
        }

        JsonNode firstChoice = choicesNode.get(0);
        if (firstChoice == null) {
            return "Sorry, I could not process your request (invalid choice structure).";
        }

        JsonNode messageNode = firstChoice.get("message");
        if (messageNode == null) {
            return "Sorry, I could not process your request (missing message field).";
        }

        JsonNode contentNode = messageNode.get("content");
        if (contentNode == null || contentNode.isNull()) {
            return "Sorry, I could not process your request (missing content field).";
        }

        return contentNode.asText();
    }
}
