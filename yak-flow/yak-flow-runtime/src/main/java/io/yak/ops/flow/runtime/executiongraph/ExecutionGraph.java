package io.yak.ops.flow.runtime.executiongraph;

import io.yak.ops.core.api.common.JobExecutionResult;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.connector.sink.BatchExecutionHook;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.flow.runtime.checkpoint.CheckpointCoordinator;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.checkpoint.LocalCompletedCheckpointStore;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import io.yak.ops.flow.runtime.jobgraph.JobVertex;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Single-job execution ownership: physical subtask attempts, job status and cancellation.
 * Task deployment and local channels are delegated to TaskDeployment; JobClient is only a handle.
 */
public final class ExecutionGraph {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionGraph.class);

    private final JobGraph jobGraph;
    private final List<ExecutionJobVertex> jobVertices;
    private final Map<Integer, ExecutionJobVertex> byVertexId;
    private final Object monitor = new Object();
    private final AtomicLong plannedSplits = new AtomicLong();
    private final AtomicLong completedSplits = new AtomicLong();
    private final CompletableFuture<JobExecutionResult> result = new CompletableFuture<>();
    private final CompletableFuture<Void> cancellation = new CompletableFuture<>();
    private volatile CompletableFuture<CheckpointCoordinator> checkpointController = new CompletableFuture<>();
    private final boolean checkpointConfigured;

    private volatile JobStatus status = JobStatus.CREATED;
    private volatile boolean cancellationRequested;
    private volatile BatchExecutionHook activeHook;
    private Thread worker;

    public ExecutionGraph(JobGraph jobGraph) {
        this.jobGraph = Objects.requireNonNull(jobGraph, "jobGraph");
        this.checkpointConfigured = !jobGraph.configuration()
                        .get(CheckpointingOptions.CHECKPOINTING_INTERVAL)
                        .isZero()
                || jobGraph.configuration().get(CheckpointingOptions.RESTORE_LATEST);
        Map<Integer, ExecutionJobVertex> index = new LinkedHashMap<>();
        for (JobVertex vertex : jobGraph.getVertices()) {
            if (index.putIfAbsent(vertex.getId(), new ExecutionJobVertex(vertex)) != null) {
                throw new IllegalArgumentException("Duplicate JobVertex id");
            }
        }
        this.byVertexId = Map.copyOf(index);
        this.jobVertices =
                jobGraph.getVertices().stream().map(v -> index.get(v.getId())).toList();
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

    /** Measured completed/planned bounded splits; 100% is reserved for successful completion. */
    public OptionalInt getProgressPercent() {
        long total = plannedSplits.get();
        if (total == 0) {
            return OptionalInt.empty();
        }
        long completed = completedSplits.get();
        return OptionalInt.of((int) Math.min(99, Math.floor(100.0 * Math.min(completed, total) / total)));
    }

    /** Records a bounded Source's planned work units for this physical attempt. */
    public void reportPlannedSplits(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Planned split count cannot be negative");
        }
        plannedSplits.addAndGet(count);
    }

    /** Records split completion after all preceding source records have been emitted. */
    public void reportCompletedSplits(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Completed split count cannot be negative");
        }
        completedSplits.addAndGet(count);
    }

    Execution currentExecution(int jobVertexId, int subtask) {
        ExecutionJobVertex vertex = byVertexId.get(jobVertexId);
        if (vertex == null) {
            throw new IllegalArgumentException("Unknown JobVertex: " + jobVertexId);
        }
        return vertex.getTaskVertex(subtask).getCurrentExecutionAttempt();
    }

    void registerCheckpoint(CheckpointCoordinator controller) {
        if (!checkpointController.complete(Objects.requireNonNull(controller, "controller"))) {
            throw new IllegalStateException("A job may register a single CheckpointCoordinator");
        }
    }

    boolean isCancellationRequested() {
        return cancellationRequested;
    }

    /** Submits this Job exactly once; the worker is owned by the ExecutionGraph, not the JobClient. */
    public void start() {
        String logId = jobGraph.configuration().get(ExecutionOptions.LOG_ID);
        boolean debug = jobGraph.configuration().get(ExecutionOptions.DEBUG);
        Thread thread = Thread.ofVirtual().name("yak-local-job-" + getJobID()).unstarted(() -> {
            if (logId == null || logId.isBlank()) {
                run();
            } else {
                try (MDC.MDCCloseable ignored = MDC.putCloseable("taskLogId", logId);
                        MDC.MDCCloseable details = MDC.putCloseable("taskLogDebug", Boolean.toString(debug))) {
                    run();
                }
            }
        });
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

        LOG.info("YakFlow Job started, jobId={}", getJobID());
        long startedNanos = System.nanoTime();
        List<BatchExecutionHook> hooks;
        try {
            hooks = jobGraph.graph().getStreamNodes().stream()
                    .filter(node -> node.isSink())
                    .flatMap(node -> node.getSink().orElseThrow().batchExecutionHooks().stream())
                    .toList();
        } catch (Throwable failure) {
            completeFailed(failure);
            return;
        }
        BatchExecutionJournal journal = null;
        Throwable problem = null;
        try {
            journal = hooks.isEmpty() ? null : new BatchExecutionJournal(jobGraph, hooks);
            if (journal != null && journal.dataStarted() && !hasCompletedCheckpoint()) {
                throw new IllegalStateException(
                        "Started batch data has no verified checkpoint; replay is not authorized");
            }
            runAttempts(journal);
        } catch (Throwable failure) {
            problem = failure;
        } finally {
            if (journal != null) {
                try {
                    journal.close();
                } catch (IOException closing) {
                    if (problem == null) problem = closing;
                    else problem.addSuppressed(closing);
                }
            }
        }
        // A terminal result releases ownership of the state directory before a caller can recover it.
        if (problem == null) {
            completeNormally(TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startedNanos)));
        } else if (cancellationRequested
                && (journal == null || !journal.hasUnconfirmedHook())
                && (isCancellationSignal(problem)
                        || problem instanceof io.yak.ops.core.api.connector.sink.BatchExecutionHookException)) {
            completeCanceled();
        } else {
            completeFailed(problem);
        }
    }

    private void runAttempts(BatchExecutionJournal journal) throws Throwable {
        int maxRestarts = jobGraph.configuration().get(ExecutionOptions.MAX_RESTART_ATTEMPTS);
        int attempt = 0;
        while (true) {
            plannedSplits.set(0);
            completedSplits.set(0);
            try {
                if (journal != null) {
                    journal.execute(
                            BatchExecutionHook.Phase.BEFORE_DATA,
                            this::isCancellationRequested,
                            hook -> activeHook = hook);
                }
                if (journal == null || !journal.dataCompleted()) {
                    if (cancellationRequested) throw new CancellationException("Data execution was cancelled");
                    if (journal != null) journal.beginData();
                    new TaskDeployment(this, attempt > 0).deployAndAwait();
                    if (cancellationRequested) throw new CancellationException("Data execution was cancelled");
                    if (journal != null) journal.completeData();
                }
                if (journal != null) {
                    journal.execute(
                            BatchExecutionHook.Phase.AFTER_DATA,
                            this::isCancellationRequested,
                            hook -> activeHook = hook);
                }
                if (!jobGraph.isBounded() && !cancellationRequested) {
                    throw new IllegalStateException("Unbounded pipeline terminated without cancellation");
                }
                return;
            } catch (Throwable failure) {
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                boolean uncertainHook = journal != null && journal.hasUnconfirmedHook();
                if (!cancellationRequested
                        && !uncertainHook
                        && attempt < maxRestarts
                        && ((journal != null && journal.dataCompleted()) || hasCompletedCheckpoint())) {
                    try {
                        if (journal == null || !journal.dataCompleted()) {
                            for (ExecutionJobVertex vertex : jobVertices) {
                                for (ExecutionVertex subtask : vertex.getTaskVertices()) {
                                    subtask.resetForNewAttempt();
                                }
                            }
                        }
                        // A checkpoint request for the old attempt must never access its closed coordinator.
                        checkpointController = new CompletableFuture<>();
                        attempt++;
                        LOG.warn("YakFlow Job resuming durable execution, jobId={}, restart={}", getJobID(), attempt);
                        continue;
                    } catch (Throwable resetFailure) {
                        failure.addSuppressed(resetFailure);
                    }
                }
                throw failure;
            }
        }
    }

    /**
     * Only a verified durable checkpoint authorizes automatic restart. Without one, fail closed:
     * restarting the Reader from offset zero could duplicate or lose already-emitted data.
     */
    private boolean hasCompletedCheckpoint() {
        String directory = jobGraph.configuration().get(CheckpointingOptions.STATE_DIRECTORY);
        if (directory == null || directory.isBlank()) {
            return false;
        }
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(Path.of(directory))) {
            return store.loadLatest(LocalCompletedCheckpointStore.graphSignature(
                            jobGraph.graph(),
                            jobGraph.configuration().get(PipelineOptions.MAX_PARALLELISM),
                            jobGraph.configuration().get(CheckpointingOptions.DEFINITION_IDENTITY)))
                    .isPresent();
        } catch (IOException | RuntimeException notRestorable) {
            return false;
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
            LOG.info("YakFlow Job finished, jobId={}, durationMs={}", getJobID(), durationMillis);
            result.complete(new JobExecutionResult(getJobID(), durationMillis));
        }
    }

    private static boolean isCancellationSignal(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof InterruptedException || cause instanceof CancellationException;
    }

    private void completeCanceled() {
        synchronized (monitor) {
            status = JobStatus.CANCELED;
        }
        checkpointController.completeExceptionally(new CancellationException("Job was cancelled"));
        notifyCanceled();
    }

    private void notifyCanceled() {
        LOG.info("YakFlow Job canceled, jobId={}", getJobID());
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
        LOG.error("YakFlow Job failed, jobId={}", getJobID(), failure);
        result.completeExceptionally(failure);
        if (wasCancellationRequested) {
            cancellation.completeExceptionally(failure);
        }
    }

    /**
     * Requests cancellation of all currently running task attempts.
     *
     * <p>The returned future completes after cancellation and resource cleanup.
     * Finished or failed jobs reject new cancellation requests.
     *
     * @return a future for the final cancellation outcome
     */
    public CompletableFuture<Void> cancel() {
        Thread running;
        synchronized (monitor) {
            if (status == JobStatus.CANCELED) {
                return CompletableFuture.completedFuture(null);
            }
            if (status == JobStatus.FAILED || status == JobStatus.FINISHED) {
                return CompletableFuture.failedFuture(new IllegalStateException("Job has already finished: " + status));
            }
            if (cancellationRequested) {
                return cancellation.copy();
            }
            cancellationRequested = true;
            status = JobStatus.CANCELLING;
            running = worker;
        }
        BatchExecutionHook hook = activeHook;
        if (hook != null) {
            Thread.ofVirtual().name("yak-batch-hook-cancel-" + getJobID()).start(() -> {
                try {
                    hook.cancel();
                } catch (Exception failure) {
                    cancellation.completeExceptionally(failure);
                }
            });
        } else if (running != null) {
            running.interrupt();
        }
        return cancellation.copy();
    }

    /**
     * Triggers an aligned checkpoint of the running job.
     *
     * <p>Checkpointing must be configured and the job must not be terminal or canceling.
     * Successful completion means the checkpoint was published durably, not that a
     * transaction has been committed.
     *
     * @return the completed snapshot or an exceptionally completed future on failure
     */
    public CompletableFuture<CheckpointSnapshot> checkpoint() {
        if (!checkpointConfigured) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Persistent checkpoints are not enabled for this job"));
        }
        if (status.isTerminalState() || cancellationRequested) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Cannot trigger a checkpoint for a terminal or cancelling job"));
        }
        return checkpointController
                .thenCompose(CheckpointCoordinator::triggerCheckpoint)
                .copy();
    }

    public CompletableFuture<JobExecutionResult> getJobExecutionResult() {
        return result.copy();
    }
}
