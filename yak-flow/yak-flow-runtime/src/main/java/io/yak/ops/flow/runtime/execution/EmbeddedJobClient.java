package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.common.JobExecutionResult;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.executiongraph.ExecutionGraph;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Client-side view of one embedded ExecutionGraph; no worker thread or duplicated job state. */
public final class EmbeddedJobClient implements JobClient {

    private final ExecutionGraph executionGraph;

    EmbeddedJobClient(ExecutionGraph executionGraph) {
        this.executionGraph = Objects.requireNonNull(executionGraph, "executionGraph");
    }

    public ExecutionGraph getExecutionGraph() {
        return executionGraph;
    }

    @Override
    public JobID getJobID() {
        return executionGraph.getJobID();
    }

    @Override
    public CompletableFuture<JobStatus> getJobStatus() {
        return CompletableFuture.completedFuture(executionGraph.getState());
    }

    @Override
    public CompletableFuture<Void> cancel() {
        return executionGraph.cancel();
    }

    @Override
    public CompletableFuture<JobExecutionResult> getJobExecutionResult() {
        return executionGraph.getJobExecutionResult();
    }

    /** Retains the existing embedded Source → Sink checkpoint API and storage format. */
    public CompletableFuture<CheckpointSnapshot> checkpoint() {
        return executionGraph.checkpoint();
    }
}
