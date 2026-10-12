package io.yak.ops.plugin.task.api.execution;

/** Authoritative terminal outcome of one physical Task execution. */
public enum TaskOutcome {
    FINISHED,
    FAILED,
    CANCELED
}
