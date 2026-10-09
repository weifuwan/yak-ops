package io.yak.ops.flow.runtime.executiongraph;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import java.util.Objects;

/** Stable physical subtask index; the Execution object represents its current attempt. */
public final class ExecutionVertex {

    private final ExecutionJobVertex jobVertex;
    private final int subtaskIndex;
    private final Execution currentExecution;

    ExecutionVertex(ExecutionJobVertex jobVertex, int subtaskIndex) {
        this.jobVertex = Objects.requireNonNull(jobVertex, "jobVertex");
        if (subtaskIndex < 0 || subtaskIndex >= jobVertex.getJobVertex().getParallelism()) {
            throw new IllegalArgumentException("Invalid subtask index");
        }
        this.subtaskIndex = subtaskIndex;
        this.currentExecution = new Execution(this, 0);
    }

    public ExecutionJobVertex getJobVertex() {
        return jobVertex;
    }

    public int getSubtaskIndex() {
        return subtaskIndex;
    }

    public Execution getCurrentExecutionAttempt() {
        return currentExecution;
    }

    RuntimeTaskInfo taskInfo(JobID jobID) {
        return new RuntimeTaskInfo(
                jobID, jobVertex.getJobVertex().getId(), subtaskIndex,
                jobVertex.getJobVertex().getParallelism(), currentExecution.getAttemptNumber());
    }
}
