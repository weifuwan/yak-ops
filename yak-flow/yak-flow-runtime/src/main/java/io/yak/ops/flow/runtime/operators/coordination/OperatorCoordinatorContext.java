package io.yak.ops.flow.runtime.operators.coordination;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import java.util.Objects;

/**
 * Immutable coordinator-side identity and resolved parallelism of one Source operator.
 *
 * <p>Contains the coordinator's stable Job and operator identity and resolved parallelism.
 * Registered reader attempts own their attempt-specific state and lifecycle.
 */
public record OperatorCoordinatorContext(JobID jobID, int operatorId, int parallelism) {

    public OperatorCoordinatorContext {
        Objects.requireNonNull(jobID, "jobID must not be null");
        if (operatorId <= 0) {
            throw new IllegalArgumentException("operatorId must be positive");
        }
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be positive");
        }
    }

    /** Rejects readers belonging to another job, operator or resolved parallelism. */
    public void validateTask(RuntimeTaskInfo taskInfo) {
        Objects.requireNonNull(taskInfo, "taskInfo must not be null");
        if (!jobID.equals(taskInfo.jobID())
                || operatorId != taskInfo.operatorId()
                || parallelism != taskInfo.parallelism()) {
            throw new IllegalArgumentException("Reader task identity does not match the SourceCoordinator");
        }
    }
}
