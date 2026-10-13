package io.yak.ops.business.task.submission;

/** Admits manual Task submissions independently of the physical execution engine. */
public interface SubmissionService {

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
