package io.yak.ops.core.api.common;

/** Lifecycle status of one submitted job, independent of batch or stream mode. */
public enum JobStatus {
    CREATED,
    RUNNING,
    FAILING,
    FAILED,
    CANCELLING,
    CANCELED,
    FINISHED;

    public boolean isTerminalState() {
        return this == FAILED || this == CANCELED || this == FINISHED;
    }
}
