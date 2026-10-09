package io.yak.ops.flow.runtime.operators.coordination;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import java.util.Objects;

/**
 * Immutable coordinator-side identity and resolved parallelism of one Source operator.
 *
 * <p>Unlike a TaskEnvironment, this context owns no active Reader, task cancellation
 * signal or configuration. Attempt numbers belong to registered reader attempts.
 */
public record OperatorCoordinatorContext(JobID jobID, int operatorId, int parallelism) {

    public OperatorCoordinatorContext {
        Objects.requireNonNull(jobID, "jobID 不能为空");
        if (operatorId <= 0) {
            throw new IllegalArgumentException("operatorId 必须为正整数");
        }
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism 必须为正整数");
        }
    }

    /** Rejects readers belonging to another job, operator or resolved parallelism. */
    public void validateTask(RuntimeTaskInfo taskInfo) {
        Objects.requireNonNull(taskInfo, "taskInfo 不能为空");
        if (!jobID.equals(taskInfo.jobID())
                || operatorId != taskInfo.operatorId()
                || parallelism != taskInfo.parallelism()) {
            throw new IllegalArgumentException("Reader RuntimeTaskInfo 与 SourceCoordinator 不匹配");
        }
    }
}
