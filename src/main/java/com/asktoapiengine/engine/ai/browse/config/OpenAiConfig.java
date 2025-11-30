package com.asktoapiengine.engine.ai.browse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Central WebClient configuration for calling OpenAI Chat APIs.
 * Later we will reuse the same pattern for SparkAssist by just changing
 * base URL / headers or wiring a different client implementation.
 */
@Configuration
public class OpenAiConfig {

    /**
     * WebClient used for calling OpenAI's HTTP APIs.
     * Currently uses the same API key as spring.ai.openai.* config.
     */
    @Bean
    public WebClient openAiWebClient(
            @Value("${spring.ai.openai.api-key}") String apiKey) {

        return WebClient.builder()
                .baseUrl("https://api.openai.com/v1") // we'll call /chat/completions on this base
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
