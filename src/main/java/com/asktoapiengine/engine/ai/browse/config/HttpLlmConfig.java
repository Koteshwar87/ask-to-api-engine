package com.asktoapiengine.engine.ai.browse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for the HTTP client used to call OpenAI-compatible
 * LLM providers (OpenAI cloud, local Ollama, and future HTTP-based providers).
 *
 * This WebClient is shared by all HTTP-based LLM adapters.
 * The actual base URL, API key and model are controlled by the llm.http.* properties.
 */
@Slf4j
@Configuration
public class HttpLlmConfig {

    /**
     * Builds a WebClient for OpenAI-compatible HTTP endpoints.
     *
     * Typical configurations:
     *  - OpenAI cloud:
     *      llm.http.base-url = https://api.openai.com/v1
     *      llm.http.api-key  = <OpenAI API key>
     *
     *  - Local Ollama:
     *      llm.http.base-url = http://localhost:11434/v1
     *      llm.http.api-key  = ollama (value is ignored by Ollama but required by this client)
     *
     * The Content-Type and Accept headers are set to application/json.
     * The Authorization header is added only when an API key is present.
     */
    @Bean
    public WebClient llmHttpWebClient(
            @Value("${llm.http.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${llm.http.api-key:}") String apiKey) {

        log.info("HttpLlmConfig: configuring WebClient for LLM HTTP client with baseUrl={}", baseUrl);

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (apiKey != null && !apiKey.isBlank()) {
            log.info("HttpLlmConfig: LLM HTTP client will use Authorization header based on configured API key");
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        } else {
            log.info("HttpLlmConfig: LLM HTTP client will be created without Authorization header (no API key configured)");
        }

        return builder.build();
    }
}

