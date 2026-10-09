package io.yak.ops.core.api.connector.sink;

import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.configuration.Configuration;

/**
 * Read-only task metadata and configuration supplied when creating a Sink Writer.
 *
 * <p>Each Writer is initialized for one task attempt; changing the returned configuration
 * must not alter the task's effective runtime configuration.
 */
public interface WriterInitContext {

    /** Returns the current subtask's identity, attempt number and parallelism. */
    TaskInfo getTaskInfo();

    /** Returns an independent copy of the effective job configuration. */
    Configuration getConfiguration();
}
