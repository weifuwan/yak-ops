package org.apache.flink.cdc.common.model.abilities;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.common.model.AiModelClient;

/** Ability interface for {@link AiModelClient} implementations that can embed image bytes. */
@Experimental
public interface SupportsImageEmbedding {

    /** Converts the given image bytes into a dense float vector. */
    float[] embedImage(byte[] image);
}
