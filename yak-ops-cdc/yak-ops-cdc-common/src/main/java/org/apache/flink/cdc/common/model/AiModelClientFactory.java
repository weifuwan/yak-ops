package org.apache.flink.cdc.common.model;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.common.factories.Factory;

/**
 * A {@link Factory} that creates {@link AiModelClient} instances. The factory identifier maps to
 * the {@code type} field of a pipeline model definition.
 */
@Experimental
public interface AiModelClientFactory extends Factory {

    /**
     * Creates a new {@link AiModelClient} from the given context. The returned client is serialized
     * with the transform operator and opened on task managers.
     */
    AiModelClient createClient(ModelContext context);
}
