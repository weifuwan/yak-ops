package io.yak.ops.business.task.execution;

/** Manages durable business submissions independently of an execution engine. */
public interface ExecutionService {

    /**
     * Admits an authorized manual run or returns its original idempotent submission.
     *
     * @param definitionId stable Task Definition to execute
     * @param requestId caller-stable idempotency key for this run
     * @param operatorId actor requesting execution
     * @return accepted or previously admitted Instance and Attempt identity
     */
    SubmissionResult submitManual(String definitionId, String requestId, String operatorId);
}
