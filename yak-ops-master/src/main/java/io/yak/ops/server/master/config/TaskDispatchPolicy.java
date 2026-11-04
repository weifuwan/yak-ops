package io.yak.ops.server.master.config;

import java.time.Duration;

import lombok.Data;

/**
 * Configuration for the master's task dispatch policy.
 * When enabled, tasks that remain in the dispatch queue longer than
 * {@link #maxTaskDispatchDuration} will be marked as failed to prevent indefinite queuing.
 */
@Data
public class TaskDispatchPolicy {

    /**
     * Indicates whether the dispatch timeout checking mechanism is enabled.
     */
    private boolean dispatchTimeoutEnabled = false;

    /**
     * The maximum allowed duration a task may wait in the dispatch queue before being assigned to a worker.
     * Tasks that exceed this duration will be marked as failed.
     * Examples: {@code "10m"}, {@code "30m"}, {@code "1h"}.
     */
    private Duration maxTaskDispatchDuration;
}
