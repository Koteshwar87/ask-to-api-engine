package com.asktoapiengine.engine.ai.browse.llm;

/**
 * Common interface for all Large Language Model (LLM) providers
 * used by the Browse flow.
 *
 * The rest of the application (services, controllers) should depend
 * only on this interface and not on any specific LLM implementation.
 */
public interface LlmClient {

    /**
     * Sends the given prompt to the underlying LLM provider
     * and returns the generated answer as plain text.
     *
     * @param prompt human-readable prompt prepared by the application
     * @return answer returned by the LLM provider
     */
    String generate(String prompt);
}
