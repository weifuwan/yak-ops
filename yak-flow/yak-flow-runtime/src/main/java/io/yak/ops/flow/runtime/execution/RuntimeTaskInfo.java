package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.TaskInfo;
import java.util.Objects;

/**
 * Immutable identity and resolved execution settings for one local subtask attempt.
 *
 * <p>{@code parallelism} is the current operator's subtask count, whereas
 * {@code maxParallelism} fixes the key-group count used for keyed state.
 * The numeric operator ID is graph-local and is not a stable checkpoint UID.
 */
public record RuntimeTaskInfo(
        JobID jobID, int operatorId, int subtaskIndex, int parallelism, int attemptNumber, int maxParallelism)
        implements TaskInfo {

    public RuntimeTaskInfo {
        Objects.requireNonNull(jobID, "jobID must not be null");
        if (operatorId <= 0) {
            throw new IllegalArgumentException("operatorId must be positive");
        }
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be positive");
        }
        if (subtaskIndex < 0 || subtaskIndex >= parallelism) {
            throw new IllegalArgumentException("subtaskIndex is outside the parallelism range");
        }
        if (maxParallelism < parallelism || maxParallelism > 32768) {
            throw new IllegalArgumentException("maxParallelism must be at least parallelism and at most 32768");
        }
        if (attemptNumber < 0) {
            throw new IllegalArgumentException("attemptNumber must not be negative");
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

    /** Returns a diagnostic task-thread name derived from its execution identity. */
    public String threadName() {
        return "yak-stream-task-" + jobID.toHexString() + "-" + operatorId + "-" + subtaskIndex + "-attempt-"
                + attemptNumber;
    }
}
