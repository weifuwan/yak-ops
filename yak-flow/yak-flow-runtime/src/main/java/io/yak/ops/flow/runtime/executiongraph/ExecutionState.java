package io.yak.ops.flow.runtime.executiongraph;

/** State of one physical subtask attempt, distinct from the job's JobStatus. */
public enum ExecutionState {
    CREATED,
    DEPLOYING,
    RUNNING,
    FINISHED,
    CANCELLING,
    CANCELED,
    FAILED
}
