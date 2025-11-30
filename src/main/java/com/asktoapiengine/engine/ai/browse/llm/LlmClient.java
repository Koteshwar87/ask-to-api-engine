package com.asktoapiengine.engine.ai.browse.llm;

/**
 * Simple abstraction over any Large Language Model (LLM).
 *
 * For now, it just exposes a single "generate" method which takes a
 * plain text prompt and returns a plain text response.
 *
 * This allows us to swap OpenAI (ChatGPT), SparkAssist, or any other
 * provider without changing the higher-level services.
 */
public interface LlmClient {

    /**
     * Generate a textual response for the given prompt.
     *
     * @param prompt the full prompt text (already formatted)
     * @return the LLM's textual response
     */
    String generate(String prompt);
}
