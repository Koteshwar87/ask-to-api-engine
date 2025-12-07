package com.asktoapiengine.engine.ai.browse.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * LLM client implementation that uses Spring AI ChatModel
 * to call the OpenAI Chat Completions API.
 *
 * This adapter is created only when llm.provider is set to OPENAI_CHATMODEL.
 * It allows the application to use the ChatModel-based integration
 * instead of the HTTP WebClient-based one.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "OPENAI_CHATMODEL")
@RequiredArgsConstructor
public class OpenAiChatModelLlmClient implements LlmClient {

    /**
     * ChatModel provided by Spring AI, configured using spring.ai.openai.* properties.
     */
    private final ChatModel chatModel;

    /**
     * Sends the prompt to the LLM using Spring AI ChatModel and returns the answer text.
     *
     * @param promptText human-readable prompt prepared by the application
     * @return answer text returned by the LLM provider
     */
    @Override
    public String generate(String promptText) {
        log.info("OpenAiChatModelLlmClient: sending prompt to LLM provider using Spring AI ChatModel");

        Prompt prompt = new Prompt(promptText);
        var response = chatModel.call(prompt);

        /*
         * ChatModel returns a ChatResponse where the main answer text
         * is available via getResult().getOutput().getText().
         */
        String content = response.getResult().getOutput().getText();
        log.info("OpenAiChatModelLlmClient: successfully received answer from LLM provider via ChatModel");

        return content;
    }
}
