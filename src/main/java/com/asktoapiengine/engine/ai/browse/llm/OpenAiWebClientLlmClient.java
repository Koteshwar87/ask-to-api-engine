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
 * LLM client implementation that calls the OpenAI Chat Completions API
 * using an OpenAI-compatible HTTP endpoint.
 *
 * This adapter is active only when:
 *   llm.provider = OPENAI_HTTP
 *
 * Typical configuration (application.yaml):
 *   llm:
 *     provider: OPENAI_HTTP
 *     http:
 *       base-url: https://api.openai.com/v1
 *       api-key: ${OPENAI_API_KEY}
 *       model: gpt-4o-mini
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "OPENAI_HTTP")
@RequiredArgsConstructor
public class OpenAiWebClientLlmClient implements LlmClient {

    /**
     * WebClient configured in HttpLlmConfig.
     * For this adapter, it should point to the OpenAI base URL.
     */
    private final WebClient llmHttpWebClient;

    /**
     * Model name used for HTTP-based LLM calls.
     * Resolution order:
     *  1. llm.http.model
     *  2. spring.ai.openai.chat.options.model
     *  3. "gpt-4o-mini" as a safe default
     */
    @Value("${llm.http.model:${spring.ai.openai.chat.options.model:gpt-4o-mini}}")
    private String modelName;

    /**
     * Sends the prompt to the configured OpenAI-compatible HTTP endpoint
     * using the /chat/completions API and returns the first answer text.
     *
     * @param prompt human-readable prompt prepared by the application
     * @return answer text returned by OpenAI
     */
    @Override
    public String generate(String prompt) {
        log.info("OpenAiWebClientLlmClient: sending prompt to OpenAI HTTP provider using model={}", modelName);

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
                .block(Duration.ofSeconds(120));

        log.info("OpenAiWebClientLlmClient: received HTTP response from OpenAI provider");

        if (response == null) {
            log.info("OpenAiWebClientLlmClient: response body is null");
            return "Sorry, I could not process your request because the OpenAI response was empty.";
        }

        JsonNode choicesNode = response.get("choices");
        if (choicesNode == null || !choicesNode.isArray() || choicesNode.isEmpty()) {
            log.info("OpenAiWebClientLlmClient: 'choices' field is missing or empty in OpenAI response");
            return "Sorry, I could not process your request because OpenAI returned no choices.";
        }

        JsonNode firstChoice = choicesNode.get(0);
        if (firstChoice == null) {
            log.info("OpenAiWebClientLlmClient: first element in 'choices' array is null");
            return "Sorry, I could not process your request because the OpenAI response was invalid.";
        }

        JsonNode messageNode = firstChoice.get("message");
        if (messageNode == null) {
            log.info("OpenAiWebClientLlmClient: 'message' field is missing in first choice");
            return "Sorry, I could not process your request because the OpenAI message field was missing.";
        }

        JsonNode contentNode = messageNode.get("content");
        if (contentNode == null || contentNode.isNull()) {
            log.info("OpenAiWebClientLlmClient: 'content' field is missing in message");
            return "Sorry, I could not process your request because the OpenAI content field was missing.";
        }

        String content = contentNode.asText();
        log.info("OpenAiWebClientLlmClient: successfully extracted answer from OpenAI response");

        return content;
    }
}
