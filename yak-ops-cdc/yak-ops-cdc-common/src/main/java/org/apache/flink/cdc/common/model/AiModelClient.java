package org.apache.flink.cdc.common.model;

import org.apache.flink.cdc.common.annotation.Experimental;

import java.io.Serializable;

/**
 * Marker interface for a runtime AI model client. Concrete capabilities are declared via ability
 * interfaces in {@code org.apache.flink.cdc.common.model.abilities}.
 *
 * <p>Implementations must be {@link Serializable} so that they can be distributed across Flink task
 * managers together with the operator that holds them.
 *
 * <p>When {@code transform.async-execution.enabled} is enabled, the same client instance may be
 * invoked concurrently. Implementations used in asynchronous transforms must therefore be
 * thread-safe.
 */
@Experimental
public interface AiModelClient extends Serializable, AutoCloseable {

    default void open() throws Exception {
        // Do nothing
    }

    @Override
    default void close() throws Exception {
        // Do nothing
    }
}
