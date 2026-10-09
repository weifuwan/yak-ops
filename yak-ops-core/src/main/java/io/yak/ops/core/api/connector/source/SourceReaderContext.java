package io.yak.ops.core.api.connector.source;

import io.yak.ops.core.configuration.Configuration;

/**
 * Minimal runtime context for one SourceReader.
 *
 * <p>Exposes subtask identity and effective configuration, not database connections,
 * application task metadata or particular split instances.
 *
 * @author weifuwan
 */
public interface SourceReaderContext {

    /** Returns an independent copy of the effective configuration for this subtask. */
    Configuration getConfiguration();

    /** Returns this reader's zero-based subtask index. */
    int getIndexOfSubtask();

    /** Returns the resolved parallelism of the owning source operator. */
    int currentParallelism();

    /** Requests splits through the runtime's attempt-aware coordinator gateway. */
    void sendSplitRequest();

    /**
 * Sends a connector-defined event to the owning source coordinator.
 *
 * <p>Implementations without event routing may reject this optional operation.
 */
    default void sendSourceEventToCoordinator(SourceEvent event) {
        throw new UnsupportedOperationException("This context does not support SourceEvent transport");
    }
}
