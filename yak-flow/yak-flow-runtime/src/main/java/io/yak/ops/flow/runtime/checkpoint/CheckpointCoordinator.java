package io.yak.ops.flow.runtime.checkpoint;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import io.yak.ops.flow.runtime.schema.SchemaCoordinator;
import io.yak.ops.flow.runtime.source.coordinator.PendingCheckpointDeliveriesException;
import io.yak.ops.flow.runtime.source.coordinator.SourceCoordinator;
import io.yak.ops.flow.runtime.tasks.OneInputStreamTask;
import io.yak.ops.flow.runtime.tasks.SourceOperatorStreamTask;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Single-JVM aligned barrier checkpoint.
 *
 * <p>Freeze split assignment, snapshot readers and inject ordered barriers. Source then resumes
 * while each downstream InputGate aligns its producers. Operator/Writer state is captured by the
 * owning Task mailbox, and durability is published only after ALL downstream acknowledgements.
 *
 * <p>Only linear, single-input local graphs are supported. No distributed exactly-once.
 */
public final class CheckpointCoordinator implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointCoordinator.class);

    private final JobGraph jobGraph;
    private final Source<?, SourceSplit, Object> source;
    private final SourceCoordinator<SourceSplit, Object> coordinator;
    private final SchemaCoordinator schemaCoordinator;
    private final List<SourceOperatorStreamTask<Object, SourceSplit>> sourceTasks;
    private final List<OneInputStreamTask> inputTasks;
    private final CompletedCheckpointStore storage;
    private final String graphSignature;
    private final BooleanSupplier cancelled;
    private final Consumer<Throwable> onFailure;
    private final long timeoutMillis;
    private final long intervalMillis;
    private final long minPauseMillis;
    private final AtomicLong nextCheckpointId;
    private final boolean finalCheckpointRequired;
    private final CompletableFuture<Void> finalCheckpointCompleted = new CompletableFuture<>();
    private long checkpointDeadlineNanos;
    private long finalCheckpointDeadlineNanos;
    private final ScheduledExecutorService timer;
    private volatile Thread checkpointThread;
    private final AtomicBoolean closed = new AtomicBoolean();

    public CheckpointCoordinator(
            JobGraph plan,
            Source<?, SourceSplit, Object> source,
            SourceCoordinator<SourceSplit, Object> coordinator,
            List<SourceOperatorStreamTask<Object, SourceSplit>> sourceTasks,
            List<OneInputStreamTask> inputTasks,
            CompletedCheckpointStore storage,
            CheckpointSnapshot restored,
            BooleanSupplier cancelled,
            Consumer<Throwable> onFailure) {
        this(plan, source, coordinator, sourceTasks, inputTasks, storage, restored, cancelled, onFailure, null);
    }

    /** Adds optional runtime schema state to the same durable checkpoint cut as Source offsets. */
    public CheckpointCoordinator(
            JobGraph plan,
            Source<?, SourceSplit, Object> source,
            SourceCoordinator<SourceSplit, Object> coordinator,
            List<SourceOperatorStreamTask<Object, SourceSplit>> sourceTasks,
            List<OneInputStreamTask> inputTasks,
            CompletedCheckpointStore storage,
            CheckpointSnapshot restored,
            BooleanSupplier cancelled,
            Consumer<Throwable> onFailure,
            SchemaCoordinator schemaCoordinator) {
        this.jobGraph = Objects.requireNonNull(plan, "plan must not be null");
        String logId = plan.configuration().get(ExecutionOptions.LOG_ID);
        boolean debug = plan.configuration().get(ExecutionOptions.DEBUG);
        ThreadFactory threads =
                Thread.ofVirtual().name("yak-local-checkpoint-", 0).factory();
        this.timer = Executors.newSingleThreadScheduledExecutor(action -> threads.newThread(() -> {
            checkpointThread = Thread.currentThread();
            if (logId == null || logId.isBlank()) {
                action.run();
            } else {
                try (MDC.MDCCloseable ignored = MDC.putCloseable("taskLogId", logId);
                        MDC.MDCCloseable details = MDC.putCloseable("taskLogDebug", Boolean.toString(debug))) {
                    action.run();
                }
            }
        }));
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
        this.schemaCoordinator = schemaCoordinator;
        this.sourceTasks = List.copyOf(sourceTasks);
        this.inputTasks = List.copyOf(inputTasks);
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
        this.graphSignature = LocalCompletedCheckpointStore.graphSignature(
                plan.graph(),
                plan.configuration().get(PipelineOptions.MAX_PARALLELISM),
                plan.configuration().get(CheckpointingOptions.DEFINITION_IDENTITY));
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled must not be null");
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure must not be null");
        Duration interval = plan.configuration().get(CheckpointingOptions.CHECKPOINTING_INTERVAL);
        Duration timeout = plan.configuration().get(CheckpointingOptions.CHECKPOINTING_TIMEOUT);
        Duration minPause = plan.configuration().get(CheckpointingOptions.MIN_PAUSE_BETWEEN_CHECKPOINTS);
        this.intervalMillis = interval.toMillis();
        this.finalCheckpointRequired = plan.isBounded() && intervalMillis > 0;
        this.minPauseMillis = minPause.toMillis();
        this.timeoutMillis = timeout.toMillis();
        if (!interval.isZero() && intervalMillis <= 0) {
            throw new IllegalArgumentException("Checkpoint interval must be at least 1 ms");
        }
        this.nextCheckpointId = new AtomicLong(restored == null ? 0 : restored.checkpointId());
        if (timeoutMillis <= 0 || sourceTasks.isEmpty() || inputTasks.isEmpty()) {
            throw new IllegalArgumentException("Invalid checkpoint timeout, source tasks or input tasks");
        }
    }

    /** True only for supported bounded jobs with periodic checkpointing enabled. */
    public boolean requiresFinalCheckpoint() {
        return finalCheckpointRequired;
    }

    /** A terminal Job cannot physically finish until the final checkpoint and its listeners succeed. */
    public CompletableFuture<Void> finalCheckpointFuture() {
        return finalCheckpointCompleted.copy();
    }

    /** Starts periodic triggering and arranges the final checkpoint after all data is processed. */
    public void start() {
        if (finalCheckpointRequired) {
            List<CompletableFuture<Void>> ready = new ArrayList<>();
            for (SourceOperatorStreamTask<Object, SourceSplit> task : sourceTasks) {
                ready.add(task.allDataProcessedFuture());
            }
            for (OneInputStreamTask task : inputTasks) {
                ready.add(task.allDataProcessedFuture());
            }
            CompletableFuture.allOf(ready.toArray(CompletableFuture[]::new)).whenComplete((unused, failure) -> {
                if (failure != null) {
                    failFinalCheckpoint(failure);
                } else {
                    try {
                        timer.execute(this::runFinalCheckpoint);
                    } catch (java.util.concurrent.RejectedExecutionException error) {
                        failFinalCheckpoint(error);
                    }
                }
            });
        }
        if (intervalMillis > 0) {
            timer.scheduleWithFixedDelay(
                    () -> {
                        if (closed.get() || cancelled.getAsBoolean() || anySourceFinished()) {
                            return;
                        }
                        triggerCheckpointFromCheckpointThread(new CompletableFuture<>());
                    },
                    intervalMillis,
                    Math.max(intervalMillis, minPauseMillis),
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Requests one checkpoint on the same serialized executor used for periodic checkpoints.
     *
     * @return the durable publication outcome, independent of subsequent completion notifications
     */
    public CompletableFuture<CheckpointSnapshot> triggerCheckpoint() {
        CompletableFuture<CheckpointSnapshot> result = new CompletableFuture<>();
        if (closed.get()) {
            result.completeExceptionally(new IllegalStateException("Checkpoint coordinator is closed"));
            return result;
        }
        try {
            timer.execute(() -> triggerCheckpointFromCheckpointThread(result));
        } catch (java.util.concurrent.RejectedExecutionException error) {
            result.completeExceptionally(error);
        }
        return result.copy();
    }

    /**
     * Waits for checkpoint attempts already queued on the serialized checkpoint executor.
     *
     * <p>Job completion must not overtake a durable checkpoint's completion listeners.
     */
    public CompletableFuture<Void> awaitPendingCheckpoints() {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        try {
            timer.execute(() -> completed.complete(null));
        } catch (java.util.concurrent.RejectedExecutionException failure) {
            completed.completeExceptionally(failure);
        }
        return completed;
    }

    private void triggerCheckpointFromCheckpointThread(CompletableFuture<CheckpointSnapshot> published) {
        try {
            performCheckpoint(published, false);
        } catch (Throwable failure) {
            boolean checkpointPublished = published.isDone();
            published.completeExceptionally(failure);
            if (!closed.get() && !cancelled.getAsBoolean() && (checkpointPublished || !isCheckpointDeclined(failure))) {
                onFailure.accept(failure);
            }
        }
    }

    private void runFinalCheckpoint() {
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        finalCheckpointDeadlineNanos = System.nanoTime() + timeoutNanos * 2;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (closed.get() || cancelled.getAsBoolean()) {
                return;
            }
            CompletableFuture<CheckpointSnapshot> published = new CompletableFuture<>();
            try {
                performCheckpoint(published, true);
                finalCheckpointCompleted.complete(null);
                return;
            } catch (Throwable failure) {
                boolean publishedDurably = published.isDone() && !published.isCompletedExceptionally();
                if (!publishedDurably && attempt == 0 && isRetryableFinalFailure(failure)) {
                    continue;
                }
                failFinalCheckpoint(failure);
                return;
            }
        }
    }

    private void failFinalCheckpoint(Throwable failure) {
        finalCheckpointCompleted.completeExceptionally(failure);
        if (!closed.get() && !cancelled.getAsBoolean()) {
            onFailure.accept(failure);
        }
    }

    private static boolean isRetryableFinalFailure(Throwable failure) {
        if (isCheckpointDeclined(failure)) {
            return true;
        }
        for (Throwable error = failure; error != null; error = error.getCause()) {
            if (error instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Restores reader splits from a completed checkpoint.
     *
     * <p>Reader snapshots contain the latest offsets. They supersede older assignment entries
     * with the same split ID; replay from the completed checkpoint is at-least-once.
     */
    public static Map<Integer, List<SourceSplit>> restoreSplits(
            CheckpointSnapshot snapshot, Source<?, SourceSplit, ?> source) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        SimpleVersionedSerializer<SourceSplit> serializer = source.getSplitSerializer();
        Map<Integer, Map<String, SourceSplit>> merged = new LinkedHashMap<>();
        // The Reader checkpoint owns all assigned splits in Flink's normal Source protocol.
        deserializeInto(merged, snapshot.readerSplits(), serializer);
        Map<Integer, List<SourceSplit>> result = new LinkedHashMap<>();
        merged.forEach((index, splits) -> result.put(index, List.copyOf(splits.values())));
        return Map.copyOf(result);
    }

    public static Object restoreEnumerator(CheckpointSnapshot snapshot, Source<?, SourceSplit, Object> source)
            throws IOException {
        var state = snapshot.enumeratorState();
        if (state.version() != 1) {
            throw new IOException("Unsupported Source Coordinator envelope version: " + state.version());
        }
        return SourceCoordinator.deserializeCheckpoint(state.bytes(), source.getEnumeratorCheckpointSerializer());
    }

    private static void deserializeInto(
            Map<Integer, Map<String, SourceSplit>> result,
            Map<Integer, List<CheckpointSnapshot.SerializedState>> sections,
            SimpleVersionedSerializer<SourceSplit> serializer)
            throws IOException {
        for (var section : sections.entrySet()) {
            Map<String, SourceSplit> splits = result.computeIfAbsent(section.getKey(), id -> new LinkedHashMap<>());
            for (var state : section.getValue()) {
                SourceSplit split = Objects.requireNonNull(
                        serializer.deserialize(state.version(), state.bytes()),
                        "Deserialized source split must not be null");
                splits.put(split.splitId(), split);
            }
        }
    }

    private void performCheckpoint(CompletableFuture<CheckpointSnapshot> published, boolean terminal) throws Exception {
        ensureActive();
        long checkpointId = nextCheckpointId.incrementAndGet();
        if (!terminal && anySourceFinished()) {
            throw new CheckpointDeclinedException(checkpointId, "Source has already reached EndOfData");
        }
        CheckpointPlan checkpointPlan = calculatePlan(checkpointId);
        PendingCheckpoint pending = createPendingCheckpoint(checkpointId, checkpointPlan);
        checkpointDeadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        if (terminal) {
            checkpointDeadlineNanos = Math.min(checkpointDeadlineNanos, finalCheckpointDeadlineNanos);
        }
        boolean coordinatorFrozen = false;
        boolean schemaCoordinatorFrozen = false;
        CheckpointSnapshot.SerializedState schemaManagerState = null;
        List<SourceOperatorStreamTask<Object, SourceSplit>> paused = new ArrayList<>();
        Map<CheckpointSnapshot.OperatorSubtask, CompletableFuture<OneInputStreamTask.CheckpointState>>
                acknowledgements = new LinkedHashMap<>();
        Throwable originalFailure = null;
        // A decline in an upstream Operator must wake a checkpoint currently awaiting the Sink.
        CompletableFuture<Void> firstFailedAcknowledgement = new CompletableFuture<>();
        try {
            if (schemaCoordinator != null) {
                await(schemaCoordinator.pauseForCheckpoint());
                schemaCoordinatorFrozen = true;
                schemaManagerState = schemaCoordinator.snapshotState();
            }
            await(coordinator.pauseForCheckpoint());
            coordinatorFrozen = true;
            waitForAssignments();
            CheckpointSnapshot frozenSource = triggerTasks(
                    checkpointId, checkpointPlan, pending, paused, acknowledgements, firstFailedAcknowledgement);
            // Barriers follow captured source offsets, but the gates can align concurrently
            // with new records on faster producer channels.
            for (SourceOperatorStreamTask<Object, SourceSplit> task : paused) {
                ensureActive();
                await(task.resumeAfterCheckpoint());
            }
            paused.clear();
            await(coordinator.resumeAfterCheckpoint());
            coordinatorFrozen = false;

            Map<CheckpointSnapshot.OperatorSubtask, Map<String, CheckpointSnapshot.SerializedState>> operatorStates =
                    new LinkedHashMap<>();
            Set<CheckpointSnapshot.OperatorSubtask> finishedReports = new HashSet<>();
            for (var entry : acknowledgements.entrySet()) {
                ensureActive();
                await(CompletableFuture.anyOf(entry.getValue(), firstFailedAcknowledgement));
                OneInputStreamTask.CheckpointState state = await(entry.getValue());
                receiveAcknowledgeMessage(pending, entry.getKey());
                if (state.finished()) {
                    finishedReports.add(entry.getKey());
                } else if (!state.operatorStates().isEmpty()) {
                    operatorStates.put(entry.getKey(), state.operatorStates());
                }
            }
            Set<CheckpointSnapshot.OperatorSubtask> finishedSubtasks =
                    checkpointPlan.finishedSubtasks(checkpointId, finishedReports, operatorStates);
            Set<String> fullyFinished = checkpointPlan.fullyFinishedOperators();
            if (terminal) {
                // All last records and Sink final flushes have been acknowledged by downstream.
                // The complete terminal cut is restorable by control-only finished Tasks.
                Set<String> terminalOperators = new HashSet<>(fullyFinished);
                for (SourceOperatorStreamTask<Object, SourceSplit> task : sourceTasks) {
                    if (!task.hasReachedEndOfData()
                            || !task.allDataProcessedFuture().isDone()) {
                        throw new CheckpointDeclinedException(checkpointId, "Source EndOfData not acknowledged");
                    }
                }
                terminalOperators.add(
                        jobGraph.getVertices().getFirst().getHeadOperator().getUid());
                for (OneInputStreamTask task : inputTasks) {
                    if (!finishedReports.contains(task.checkpointIdentity())) {
                        throw new CheckpointDeclinedException(checkpointId, "Operator final output not checkpointed");
                    }
                    terminalOperators.add(task.checkpointIdentity().uid());
                }
                if (!operatorStates.isEmpty()
                        || terminalOperators.size() != jobGraph.getVertices().size()) {
                    throw new CheckpointDeclinedException(checkpointId, "Terminal checkpoint has incomplete operators");
                }
                fullyFinished = Set.copyOf(terminalOperators);
                finishedSubtasks = Set.of();
            }
            CheckpointSnapshot snapshot = new CheckpointSnapshot(
                    checkpointId,
                    graphSignature,
                    frozenSource.enumeratorState(),
                    frozenSource.readerSplits(),
                    frozenSource.assignments(),
                    System.currentTimeMillis(),
                    operatorStates,
                    fullyFinished,
                    finishedSubtasks,
                    schemaManagerState);
            if (snapshot.hasFinishedOperators() && !terminal) {
                throw new CheckpointDeclinedException(checkpointId, "Finished state requires a terminal checkpoint");
            }
            maybeCompleteCheckpoint(pending, snapshot, published);
        } catch (Exception | Error failure) {
            abortPendingCheckpoint(pending);
            originalFailure = failure;
            throw failure;
        } finally {
            if (pending.status() == PendingCheckpoint.Status.PENDING
                    || pending.status() == PendingCheckpoint.Status.ABORTED) {
                Throwable reason = originalFailure == null
                        ? new CheckpointDeclinedException(checkpointId, "checkpoint did not complete")
                        : originalFailure;
                for (OneInputStreamTask task : inputTasks) {
                    task.declineCheckpoint(checkpointId, reason);
                }
                try {
                    await(coordinator.notifyCheckpointAborted(checkpointId));
                } catch (Exception ignored) {
                    // Cleanup must not hide the original failure.
                }
            }
            Throwable resumeFailure = null;
            for (SourceOperatorStreamTask<Object, SourceSplit> sourceTask : paused) {
                try {
                    await(sourceTask.resumeAfterCheckpoint());
                } catch (Exception error) {
                    if (!cancelled.getAsBoolean() && !closed.get()) {
                        resumeFailure = accumulate(resumeFailure, error);
                    }
                }
            }
            if (coordinatorFrozen) {
                try {
                    await(coordinator.resumeAfterCheckpoint());
                } catch (Exception error) {
                    if (!cancelled.getAsBoolean() && !closed.get()) {
                        resumeFailure = accumulate(resumeFailure, error);
                    }
                }
            }
            if (schemaCoordinatorFrozen) {
                try {
                    await(schemaCoordinator.resumeAfterCheckpoint());
                } catch (Exception error) {
                    if (!cancelled.getAsBoolean() && !closed.get()) {
                        resumeFailure = accumulate(resumeFailure, error);
                    }
                }
            }
            if (resumeFailure != null) {
                if (originalFailure != null) {
                    originalFailure.addSuppressed(resumeFailure);
                } else if (resumeFailure instanceof Exception error) {
                    throw error;
                } else if (resumeFailure instanceof Error error) {
                    throw error;
                } else {
                    throw new IllegalStateException(resumeFailure);
                }
            }
        }
    }

    /** Creates the checkpoint from the tasks which must acknowledge its barrier. */
    private PendingCheckpoint createPendingCheckpoint(long checkpointId, CheckpointPlan plan) {
        return new PendingCheckpoint(checkpointId, plan.getTasksToWaitFor());
    }

    /** Receives the task acknowledgement; duplicate and unknown ACKs cannot complete a checkpoint. */
    private void receiveAcknowledgeMessage(PendingCheckpoint checkpoint, CheckpointSnapshot.OperatorSubtask task) {
        PendingCheckpoint.TaskAcknowledgeResult result = checkpoint.acknowledgeTask(task);
        if (result != PendingCheckpoint.TaskAcknowledgeResult.SUCCESS) {
            throw new IllegalStateException(
                    "Checkpoint " + checkpoint.getCheckpointID() + " rejected acknowledgement " + task + ": " + result);
        }
    }

    /** A completed durable publication must not be aborted if its callbacks fail. */
    private void abortPendingCheckpoint(PendingCheckpoint checkpoint) {
        if (checkpoint.status() == PendingCheckpoint.Status.PERSISTED) {
            checkpoint.failCompletion();
        } else if (checkpoint.status() == PendingCheckpoint.Status.PENDING) {
            checkpoint.abort();
        }
    }

    /** Triggers task barriers after Source Coordinator and Reader state has been frozen. */
    private CheckpointSnapshot triggerTasks(
            long checkpointId,
            CheckpointPlan checkpointPlan,
            PendingCheckpoint pending,
            List<SourceOperatorStreamTask<Object, SourceSplit>> paused,
            Map<CheckpointSnapshot.OperatorSubtask, CompletableFuture<OneInputStreamTask.CheckpointState>>
                    acknowledgements,
            CompletableFuture<Void> firstFailedAcknowledgement)
            throws Exception {
        // Register the running participants before injecting any trigger barrier.
        for (OneInputStreamTask task : inputTasks) {
            CheckpointSnapshot.OperatorSubtask id = task.checkpointIdentity();
            if (!checkpointPlan.getTasksToWaitFor().contains(id)) {
                continue;
            }
            CompletableFuture<OneInputStreamTask.CheckpointState> acknowledgement = task.expectCheckpoint(checkpointId);
            acknowledgement.whenComplete((unused, error) -> {
                if (error != null) {
                    firstFailedAcknowledgement.completeExceptionally(error);
                }
            });
            if (acknowledgements.putIfAbsent(id, acknowledgement) != null) {
                throw new IllegalStateException("Duplicate operator state identity: " + id);
            }
        }
        Map<Integer, List<SourceSplit>> readerStates = new LinkedHashMap<>();
        String sourceUid = jobGraph.getVertices().getFirst().getHeadOperator().getUid();
        for (SourceOperatorStreamTask<Object, SourceSplit> sourceTask : sourceTasks) {
            CheckpointSnapshot.OperatorSubtask id = new CheckpointSnapshot.OperatorSubtask(
                    sourceUid, sourceTask.taskInfo().subtaskIndex());
            if (!checkpointPlan.getTasksToTrigger().contains(id)) {
                continue;
            }
            ensureActive();
            List<SourceSplit> splits;
            try {
                splits = await(sourceTask.pauseAndEmitBarrier(checkpointId));
            } catch (Exception error) {
                if (sourceTask.hasFinishedOutput()
                        || sourceTask.completionFuture().isDone()) {
                    throw new CheckpointDeclinedException(checkpointId, "Source finished during checkpoint");
                }
                throw error;
            }
            paused.add(sourceTask);
            readerStates.put(sourceTask.taskInfo().subtaskIndex(), splits);
            receiveAcknowledgeMessage(pending, id);
        }
        CompletableFuture<byte[]> coordinatorCheckpoint = new CompletableFuture<>();
        coordinator.checkpointCoordinator(checkpointId, coordinatorCheckpoint);
        byte[] sourceState = await(coordinatorCheckpoint);
        // The Reader and Enumerator are paused *now*. Serialize their potentially mutable
        // Split/Enumerator objects before either one is allowed to resume. Downstream barrier
        // alignment may take time and must never change the recorded source offset.
        CheckpointSnapshot frozenSource = serialize(checkpointId, sourceState, readerStates, Map.of());
        for (OneInputStreamTask task : inputTasks) {
            if (checkpointPlan.getTasksToTrigger().contains(task.checkpointIdentity())) {
                await(task.triggerCheckpoint(checkpointId));
            }
        }
        return frozenSource;
    }

    /** Completes the pending checkpoint only after every task acknowledged its state. */
    private void maybeCompleteCheckpoint(
            PendingCheckpoint pending, CheckpointSnapshot snapshot, CompletableFuture<CheckpointSnapshot> published)
            throws Exception {
        if (!pending.isFullyAcknowledged()) {
            throw new IllegalStateException("Checkpoint has missing task acknowledgements");
        }
        completePendingCheckpoint(pending, snapshot, published);
    }

    /** Finalizes durable state before notifying Sink committers and Source participants. */
    private void completePendingCheckpoint(
            PendingCheckpoint pending, CheckpointSnapshot snapshot, CompletableFuture<CheckpointSnapshot> published)
            throws Exception {
        long checkpointId = pending.getCheckpointID();
        pending.finalizeCheckpoint(snapshot, storage);
        LOG.info("YakFlow checkpoint saved, jobId={}, checkpointId={}", jobGraph.jobID(), checkpointId);
        published.complete(snapshot);
        if (System.nanoTime() >= checkpointDeadlineNanos) {
            throw new IllegalStateException("Checkpoint persisted after deadline; refusing completion callback");
        }
        // Commit prepared external transactions only after storage.save(snapshot) returned.
        // Source completion is withheld until every participating Sink commit succeeds.
        for (OneInputStreamTask task : inputTasks) {
            if (task.isSinkTask() && !task.completionFuture().isDone()) {
                await(task.notifyCheckpointComplete(checkpointId));
            }
        }
        await(coordinator.notifyCheckpointComplete(checkpointId));
        for (SourceOperatorStreamTask<Object, SourceSplit> sourceTask : sourceTasks) {
            if (!sourceTask.completionFuture().isDone()) {
                await(sourceTask.notifyCheckpointComplete(checkpointId));
            }
        }
        pending.complete();
    }

    private static Throwable accumulate(Throwable previous, Throwable error) {
        if (previous == null) {
            return error;
        }
        if (previous != error) {
            previous.addSuppressed(error);
        }
        return previous;
    }

    private CheckpointSnapshot serialize(
            long checkpointId,
            byte[] sourceState,
            Map<Integer, List<SourceSplit>> readerStates,
            Map<CheckpointSnapshot.OperatorSubtask, Map<String, CheckpointSnapshot.SerializedState>> operatorStates)
            throws Exception {
        SimpleVersionedSerializer<SourceSplit> splits = source.getSplitSerializer();
        var state = new CheckpointSnapshot.SerializedState(1, sourceState);
        return new CheckpointSnapshot(
                checkpointId,
                graphSignature,
                state,
                serializeSplits(readerStates, splits),
                Map.of(),
                System.currentTimeMillis(),
                operatorStates);
    }

    private static Map<Integer, List<CheckpointSnapshot.SerializedState>> serializeSplits(
            Map<Integer, List<SourceSplit>> values, SimpleVersionedSerializer<SourceSplit> serializer)
            throws IOException {
        Map<Integer, List<CheckpointSnapshot.SerializedState>> result = new LinkedHashMap<>();
        for (var entry : values.entrySet()) {
            List<CheckpointSnapshot.SerializedState> states = new ArrayList<>();
            for (SourceSplit split : entry.getValue()) {
                states.add(
                        new CheckpointSnapshot.SerializedState(serializer.getVersion(), serializer.serialize(split)));
            }
            result.put(entry.getKey(), List.copyOf(states));
        }
        return result;
    }

    private void waitForAssignments() throws Exception {
        while (true) {
            ensureActive();
            try {
                await(coordinator.awaitCheckpointDeliveries());
                return;
            } catch (PendingCheckpointDeliveriesException pending) {
                if (System.nanoTime() >= checkpointDeadlineNanos) {
                    throw pending;
                }
                Thread.sleep(10);
            }
        }
    }

    /** Ordinary EOF can decline a checkpoint without failing the data pipeline. */
    private static boolean isCheckpointDeclined(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof CheckpointDeclinedException) {
                return true;
            }
        }
        return false;
    }

    private boolean anySourceFinished() {
        return sourceTasks.stream().anyMatch(SourceOperatorStreamTask::hasReachedEndOfData);
    }

    private CheckpointPlan calculatePlan(long checkpointId) throws CheckpointDeclinedException {
        Set<CheckpointSnapshot.OperatorSubtask> finished = new HashSet<>();
        String sourceUid = jobGraph.getVertices().getFirst().getHeadOperator().getUid();
        for (SourceOperatorStreamTask<Object, SourceSplit> task : sourceTasks) {
            if (task.completionFuture().isDone() && !task.completionFuture().isCompletedExceptionally()) {
                finished.add(new CheckpointSnapshot.OperatorSubtask(
                        sourceUid, task.taskInfo().subtaskIndex()));
            }
        }
        for (OneInputStreamTask task : inputTasks) {
            if (task.completionFuture().isDone() && !task.completionFuture().isCompletedExceptionally()) {
                finished.add(task.checkpointIdentity());
            }
        }
        return CheckpointPlan.calculate(checkpointId, jobGraph, finished);
    }

    private void ensureActive() {
        if (closed.get() || cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Checkpoint was cancelled");
        }
    }

    private <T> T await(CompletableFuture<T> future) throws Exception {
        try {
            long remainingNanos = checkpointDeadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new IllegalStateException("Checkpoint deadline exceeded");
            }
            return future.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof Exception error) {
                throw error;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (TimeoutException failure) {
            throw new IllegalStateException("Checkpoint timed out waiting for task or coordinator responses", failure);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            timer.shutdownNow();
            if (Thread.currentThread() == checkpointThread) return;
            try {
                if (!timer.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Checkpoint coordinator did not stop before recovery");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping checkpoint coordinator", failure);
            }
        }
    }
}
