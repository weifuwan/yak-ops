package io.yak.ops.task.execution;

import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.common.enums.task.TaskInstanceStatus;

/** Coordinates durable Task submission and cancellation through the embedded execution owner. */
public interface TaskExecutionService {

    /**
     * Admits and dispatches one authorized manual Task submission.
     *
     * @param definitionId published Task Definition
     * @param requestId stable idempotency key for this submission
     * @param operatorId actor requesting execution
     * @return persisted Instance and Attempt identity, including idempotent replay status
     */
    SubmissionResult submitManual(String definitionId, String requestId, String operatorId);

    /**
     * Persists cancellation intent and requests physical cancellation when a Job is active.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId Task Instance to cancel
     * @return authoritative persisted Instance status
     */
    TaskInstanceStatus cancel(String workspaceId, String instanceId);
}
