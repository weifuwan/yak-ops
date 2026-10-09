package io.yak.ops.flow.runtime.executiongraph;

import io.yak.ops.flow.runtime.jobgraph.JobVertex;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Execution-time container for all parallel subtasks of a physical JobVertex. */
public final class ExecutionJobVertex {

    private final JobVertex jobVertex;
    private final List<ExecutionVertex> taskVertices;

    ExecutionJobVertex(JobVertex jobVertex) {
        this.jobVertex = Objects.requireNonNull(jobVertex, "jobVertex");
        List<ExecutionVertex> created = new ArrayList<>(jobVertex.getParallelism());
        for (int i = 0; i < jobVertex.getParallelism(); i++) {
            created.add(new ExecutionVertex(this, i));
        }
        this.taskVertices = List.copyOf(created);
    }

    public JobVertex getJobVertex() {
        return jobVertex;
    }

    public List<ExecutionVertex> getTaskVertices() {
        return taskVertices;
    }

    public ExecutionVertex getTaskVertex(int subtaskIndex) {
        return taskVertices.get(subtaskIndex);
    }
}
