package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.TaskInfo;
import java.util.Objects;

/**
 * 一次本地执行子任务的不可变身份与实际运行参数。
 *
 * <p>parallelism 是该 Operator 的已解析并行度；maxParallelism 是本次物理图中
 * 固定的 KeyGroup 总数，不等于当前运行子任务数。operatorId 是图内 ID，而非稳定 UID。
 */
public record RuntimeTaskInfo(JobID jobID, int operatorId, int subtaskIndex, int parallelism,
                              int attemptNumber, int maxParallelism) implements TaskInfo {

    /** Compatibility constructor used by tests and existing local runtime embedders. */
    public RuntimeTaskInfo(JobID jobID, int operatorId, int subtaskIndex, int parallelism, int attemptNumber) {
        this(jobID, operatorId, subtaskIndex, parallelism, attemptNumber, 128);
    }

    public RuntimeTaskInfo {
        Objects.requireNonNull(jobID, "jobID 不能为空");
        if (operatorId <= 0) {
            throw new IllegalArgumentException("operatorId 必须为正整数");
        }
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism 必须为正整数");
        }
        if (subtaskIndex < 0 || subtaskIndex >= parallelism) {
            throw new IllegalArgumentException("subtaskIndex 超出并行度范围");
        }
        if (maxParallelism < parallelism || maxParallelism > 32768) {
            throw new IllegalArgumentException("maxParallelism 必须 >= parallelism 且 <= 32768");
        }
        if (attemptNumber < 0) {
            throw new IllegalArgumentException("attemptNumber 不能为负数");
        }
    }

    @Override
    public int getIndexOfThisSubtask() {
        return subtaskIndex;
    }

    @Override
    public int getNumberOfParallelSubtasks() {
        return parallelism;
    }

    @Override
    public int getAttemptNumber() {
        return attemptNumber;
    }

    @Override
    public int getMaxNumberOfParallelSubtasks() {
        return maxParallelism;
    }

    /** Task 线程的诊断名称，由统一运行身份生成，不从 Configuration 读取。 */
    public String threadName() {
        return "yak-stream-task-" + jobID.toHexString() + "-" + operatorId + "-" + subtaskIndex
                + "-attempt-" + attemptNumber;
    }
}
