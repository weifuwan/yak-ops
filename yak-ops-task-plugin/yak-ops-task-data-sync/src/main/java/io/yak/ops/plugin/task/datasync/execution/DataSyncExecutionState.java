package io.yak.ops.plugin.task.datasync.execution;

import org.apache.flink.api.common.JobStatus;

/** Normalized lifecycle state for a CDC execution. */
public enum DataSyncExecutionState {

    CREATED,
    RUNNING,
    FINISHED,
    CANCELED,
    FAILED;

    public boolean isTerminal() {
        return this == FINISHED || this == CANCELED || this == FAILED;
    }

    public static DataSyncExecutionState from(JobStatus status) {
        if (status == null) {
            return CREATED;
        }
        switch (status) {
            case FINISHED:
                return FINISHED;
            case CANCELED:
            case SUSPENDED:
                return CANCELED;
            case FAILED:
                return FAILED;
            case INITIALIZING:
            case CREATED:
            case RUNNING:
            case RESTARTING:
            case CANCELLING:
            case FAILING:
            case RECONCILING:
            default:
                return RUNNING;
        }
    }
}
