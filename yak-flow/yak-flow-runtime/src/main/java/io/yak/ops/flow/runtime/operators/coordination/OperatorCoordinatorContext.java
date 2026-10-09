package io.yak.ops.flow.runtime.operators.coordination;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import java.util.Objects;

/**
 * 一个 Source Operator 的协调侧不可变身份与实际并行度。
 *
 * <p>与 TaskEnvironment 分开：Coordinator 不持有 Reader、Task 取消状态或配置容器。
 * Attempt 属于已注册的 Reader，不是整个 Coordinator 的固定属性。
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

    /** 拒绝属于其它作业、算子或实际并行度的 Reader。 */
    public void validateTask(RuntimeTaskInfo taskInfo) {
        Objects.requireNonNull(taskInfo, "taskInfo 不能为空");
        if (!jobID.equals(taskInfo.jobID())
                || operatorId != taskInfo.operatorId()
                || parallelism != taskInfo.parallelism()) {
            throw new IllegalArgumentException("Reader RuntimeTaskInfo 与 SourceCoordinator 不匹配");
        }
    }
}
