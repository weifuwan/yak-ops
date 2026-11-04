package org.apache.flink.cdc.common.model.abilities;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.common.model.AiModelClient;

/**
 * Ability interface for {@link AiModelClient} implementations that can perform chat-style text
 * generation given a system prompt and a user input.
 */
@Experimental
public interface SupportsTextGeneration {

    /**
     * Generates text based on a system-level prompt and a user-provided input message. Returns a
     * JSON string conforming to the output schema declared by the calling AI function.
     */
    String generate(String systemPrompt, String userInput);
}
