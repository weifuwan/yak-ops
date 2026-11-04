package org.apache.flink.cdc.common.model.abilities;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.common.model.AiModelClient;

/**
 * Ability interface for {@link AiModelClient} implementations that can generate text from an image
 * combined with a natural-language prompt.
 */
@Experimental
public interface SupportsImageTextGeneration {

    /** Generates text from the given image bytes guided by a natural-language prompt. */
    String generateTextFromImage(byte[] image, String prompt);
}
