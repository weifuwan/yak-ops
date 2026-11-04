package org.apache.flink.cdc.common.model.abilities;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.common.model.AiModelClient;

/**
 * Ability interface for {@link AiModelClient} implementations that can produce dense vector
 * embeddings from text input.
 */
@Experimental
public interface SupportsEmbedding {

    /** Converts the given text into a dense float vector. */
    float[] embed(String text);
}
