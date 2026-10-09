package io.yak.ops.flow.runtime.executiongraph;

import io.yak.ops.core.api.common.JobExecutionResult;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.checkpoint.QuiescentCheckpointCoordinator;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import io.yak.ops.flow.runtime.jobgraph.JobVertex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Single-job execution ownership: physical subtask attempts, job status and cancellation.
 * Task deployment and local channels are delegated to TaskDeployment; JobClient is only a handle.
 */
public final class ExecutionGraph {

    private final JobGraph jobGraph;
    private final List<ExecutionJobVertex> jobVertices;
    private final Map<Integer, ExecutionJobVertex> byVertexId;
    private final Object monitor = new Object();
    private final CompletableFuture<JobExecutionResult> result = new CompletableFuture<>();
    private final CompletableFuture<Void> cancellation = new CompletableFuture<>();
    private final CompletableFuture<QuiescentCheckpointCoordinator> checkpointController = new CompletableFuture<>();
    private final boolean checkpointConfigured;

    private volatile JobStatus status = JobStatus.CREATED;
    private volatile boolean cancellationRequested;
    private Thread worker;

    public ExecutionGraph(JobGraph jobGraph) {
        this.jobGraph = Objects.requireNonNull(jobGraph, "jobGraph");
        this.checkpointConfigured = !jobGraph.configuration()
                .get(CheckpointingOptions.CHECKPOINTING_INTERVAL).isZero()
                || jobGraph.configuration().get(CheckpointingOptions.RESTORE_LATEST);
        Map<Integer, ExecutionJobVertex> index = new LinkedHashMap<>();
        for (JobVertex vertex : jobGraph.getVertices()) {
            if (index.putIfAbsent(vertex.getId(), new ExecutionJobVertex(vertex)) != null) {
                throw new IllegalArgumentException("Duplicate JobVertex id");
            }
        }
        this.byVertexId = Map.copyOf(index);
        this.jobVertices = jobGraph.getVertices().stream()
                .map(v -> index.get(v.getId()))
                .toList();
    }

    public JobGraph getJobGraph() {
        return jobGraph;
    }

    public List<ExecutionJobVertex> getJobVertices() {
        return jobVertices;
    }

    public JobID getJobID() {
        return jobGraph.jobID();
    }

    public JobStatus getState() {
        return status;
    }

    Execution currentExecution(int jobVertexId, int subtask) {
        ExecutionJobVertex vertex = byVertexId.get(jobVertexId);
        if (vertex == null) {
            throw new IllegalArgumentException("Unknown JobVertex: " + jobVertexId);
        }
        return vertex.getTaskVertex(subtask).getCurrentExecutionAttempt();
    }

    void registerCheckpoint(QuiescentCheckpointCoordinator controller) {
        if (!checkpointController.complete(Objects.requireNonNull(controller, "controller"))) {
            throw new IllegalStateException("A job may register a single CheckpointCoordinator");
        }
    }

    boolean isCancellationRequested() {
        return cancellationRequested;
    }

    /** Submits this Job exactly once; the worker is owned by the ExecutionGraph, not the JobClient. */
    public void start() {
        Thread thread = Thread.ofVirtual().name("yak-local-job-" + getJobID()).unstarted(this::run);
        synchronized (monitor) {
            if (worker != null) {
                throw new IllegalStateException("Job has already been started");
            }
            worker = thread;
        }
        thread.start();
    }

    private void run() {
        synchronized (monitor) {
            if (!cancellationRequested) {
                status = JobStatus.RUNNING;
            }
        }
        if (cancellationRequested) {
            completeCanceled();
            return;
        }

        long startNanos = System.nanoTime();
        try {
            new TaskDeployment(this).deployAndAwait();
            if (!jobGraph.isBounded() && !cancellationRequested) {
                completeFailed(new IllegalStateException("无界 Pipeline 未被取消却提前结束"));
                return;
            }
            completeNormally(TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startNanos)));
        } catch (Throwable failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (cancellationRequested
                    && (failure instanceof InterruptedException || failure instanceof CancellationException)) {
                completeCanceled();
            } else {
                completeFailed(failure);
            }
        }
    }

    private void completeNormally(long durationMillis) {
        boolean canceled;
        synchronized (monitor) {
            canceled = cancellationRequested;
            status = canceled ? JobStatus.CANCELED : JobStatus.FINISHED;
        }
        checkpointController.completeExceptionally(new IllegalStateException("Job has finished"));
        if (canceled) {
            notifyCanceled();
        } else {
            result.complete(new JobExecutionResult(getJobID(), durationMillis));
        }
    }

    private void completeCanceled() {
        synchronized (monitor) {
            status = JobStatus.CANCELED;
        }
        checkpointController.completeExceptionally(new CancellationException("Job was cancelled"));
        notifyCanceled();
    }

    private void notifyCanceled() {
        result.completeExceptionally(new CancellationException("Job was cancelled: " + getJobID()));
        cancellation.complete(null);
    }

    private void completeFailed(Throwable failure) {
        checkpointController.completeExceptionally(failure);
        boolean wasCancellationRequested;
        synchronized (monitor) {
            status = JobStatus.FAILED;
            wasCancellationRequested = cancellationRequested;
        }
        result.completeExceptionally(failure);
        if (wasCancellationRequested) {
            cancellation.completeExceptionally(failure);
        }
    }

    public CompletableFuture<Void> cancel() {
        Thread running;
        synchronized (monitor) {
            if (status == JobStatus.CANCELED) {
                return CompletableFuture.completedFuture(null);
            }
            if (status == JobStatus.FAILED || status == JobStatus.FINISHED) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Job has already finished: " + status));
            }
            if (cancellationRequested) {
                return cancellation.copy();
            }
            cancellationRequested = true;
            status = JobStatus.CANCELLING;
            running = worker;
        }
        if (running != null) {
            running.interrupt();
        }
        return cancellation.copy();
    }

    public CompletableFuture<CheckpointSnapshot> checkpoint() {
        if (!checkpointConfigured) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("该 Job 未启用持久化 Checkpoint"));
        }
        if (status.isTerminalState() || cancellationRequested) {
            return CompletableFuture.failedFuture(new IllegalStateException("已结束或取消中的 Job 不能触发 Checkpoint"));
        }
        return checkpointController.thenCompose(QuiescentCheckpointCoordinator::trigger).copy();
    }

    public CompletableFuture<JobExecutionResult> getJobExecutionResult() {
        return result.copy();
    }
}
