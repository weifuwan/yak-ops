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

    /**
     * Returns task-owned processing-time timers for the current Writer.
     *
     * <p>Callbacks execute on the owning task mailbox, not on the timer thread. Legacy test
     * contexts may omit this capability, but a Writer configured with timed flush must require it.
     */
    default ProcessingTimeService getProcessingTimeService() {
        throw new UnsupportedOperationException("Sink processing-time timers are not available");
    }
}
