package io.yak.ops.core.api.common;

/** Lifecycle status of one submitted job, independent of its execution mode. */
public enum JobStatus {
    CREATED,
    RUNNING,
    FAILING,
    FAILED,
    CANCELLING,
    CANCELED,
    FINISHED;

    /** Returns whether the job has reached a terminal status. */
    public boolean isTerminalState() {
        return this == FAILED || this == CANCELED || this == FINISHED;
    }
}
