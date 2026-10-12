package io.yak.ops.flow.runtime.checkpoint;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Single-JVM aligned barrier checkpoint.
 *
 * <p>Freeze split assignment, snapshot readers and inject ordered barriers. Source then resumes
 * while each downstream InputGate aligns its producers. Operator/Writer state is captured by the
 * owning Task mailbox, and durability is published only after ALL downstream acknowledgements.
 *
 * <p>Only linear, single-input local graphs are supported. No distributed exactly-once.
 */
public final class AlignedCheckpointCoordinator implements AutoCloseable {

    private final JobGraph jobGraph;
    private final Source<?, SourceSplit, Object> source;
    private final SourceCoordinator<SourceSplit, Object> coordinator;
    private final List<SourceOperatorStreamTask<Object, SourceSplit>> sourceTasks;
    private final List<OneInputStreamTask> inputTasks;
    private final FileCheckpointStore storage;
    private final String graphSignature;
    private final BooleanSupplier cancelled;
    private final Consumer<Throwable> onFailure;
    private final long timeoutMillis;
    private final long intervalMillis;
    private final long minPauseMillis;
    private final AtomicLong nextCheckpointId;
    private long checkpointDeadlineNanos;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("yak-local-checkpoint-", 0).factory());
    private final AtomicBoolean closed = new AtomicBoolean();

    public AlignedCheckpointCoordinator(
            JobGraph plan,
            Source<?, SourceSplit, Object> source,
            SourceCoordinator<SourceSplit, Object> coordinator,
            List<SourceOperatorStreamTask<Object, SourceSplit>> sourceTasks,
            List<OneInputStreamTask> inputTasks,
            FileCheckpointStore storage,
            CheckpointSnapshot restored,
            BooleanSupplier cancelled,
            Consumer<Throwable> onFailure) {
        this.jobGraph = Objects.requireNonNull(plan, "plan 不能为空");
        this.source = Objects.requireNonNull(source, "source 不能为空");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator 不能为空");
        this.sourceTasks = List.copyOf(sourceTasks);
        this.inputTasks = List.copyOf(inputTasks);
        this.storage = Objects.requireNonNull(storage, "storage 不能为空");
        this.graphSignature = FileCheckpointStore.graphSignature(
                plan.graph(), plan.configuration().get(PipelineOptions.MAX_PARALLELISM));
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled 不能为空");
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure 不能为空");
        Duration interval = plan.configuration().get(CheckpointingOptions.CHECKPOINTING_INTERVAL);
        Duration timeout = plan.configuration().get(CheckpointingOptions.CHECKPOINTING_TIMEOUT);
        Duration minPause = plan.configuration().get(CheckpointingOptions.MIN_PAUSE_BETWEEN_CHECKPOINTS);
        this.intervalMillis = interval.toMillis();
        this.minPauseMillis = minPause.toMillis();
        this.timeoutMillis = timeout.toMillis();
        if (!interval.isZero() && intervalMillis <= 0) {
            throw new IllegalArgumentException("Checkpoint 周期不能小于 1ms");
        }
        this.nextCheckpointId = new AtomicLong(restored == null ? 0 : restored.checkpointId());
        if (timeoutMillis <= 0 || sourceTasks.isEmpty() || inputTasks.isEmpty()) {
            throw new IllegalArgumentException("Checkpoint 超时、Source / Sink / Channel 配置非法");
        }
    }

    /** Starts periodic checkpoint triggering only after the preceding attempt has completed. */
    public void start() {
        if (intervalMillis > 0) {
            timer.scheduleWithFixedDelay(
                    () -> {
                        if (closed.get() || cancelled.getAsBoolean()) {
                            return;
                        }
                        runCheckpoint(new CompletableFuture<>());
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
    public CompletableFuture<CheckpointSnapshot> trigger() {
        CompletableFuture<CheckpointSnapshot> result = new CompletableFuture<>();
        if (closed.get()) {
            result.completeExceptionally(new IllegalStateException("CheckpointCoordinator 已关闭"));
            return result;
        }
        try {
            timer.execute(() -> runCheckpoint(result));
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

    private void runCheckpoint(CompletableFuture<CheckpointSnapshot> published) {
        try {
            performCheckpoint(published);
        } catch (Throwable failure) {
            boolean checkpointPublished = published.isDone();
            published.completeExceptionally(failure);
            if (!closed.get() && !cancelled.getAsBoolean() && (checkpointPublished || !isCheckpointDeclined(failure))) {
                onFailure.accept(failure);
            }
        }
    }

    /**
     * Restores reader splits from a completed checkpoint.
     *
     * <p>Reader snapshots contain the latest offsets. They supersede older assignment entries
     * with the same split ID; replay from the completed checkpoint is at-least-once.
     */
    public static Map<Integer, List<SourceSplit>> restoreSplits(
            CheckpointSnapshot snapshot, Source<?, SourceSplit, ?> source) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot 不能为空");
        SimpleVersionedSerializer<SourceSplit> serializer = source.getSplitSerializer();
        Map<Integer, Map<String, SourceSplit>> merged = new LinkedHashMap<>();
        deserializeInto(merged, snapshot.assignments(), serializer);
        // The reader snapshot takes precedence over older assignment state for the same split ID.
        deserializeInto(merged, snapshot.readerSplits(), serializer);
        Map<Integer, List<SourceSplit>> result = new LinkedHashMap<>();
        merged.forEach((index, splits) -> result.put(index, List.copyOf(splits.values())));
        return Map.copyOf(result);
    }

    public static Object restoreEnumerator(CheckpointSnapshot snapshot, Source<?, SourceSplit, Object> source)
            throws IOException {
        var state = snapshot.enumeratorState();
        return source.getEnumeratorCheckpointSerializer().deserialize(state.version(), state.bytes());
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
                        serializer.deserialize(state.version(), state.bytes()), "Source Split 反序列化为空");
                splits.put(split.splitId(), split);
            }
        }
    }

    private void performCheckpoint(CompletableFuture<CheckpointSnapshot> published) throws Exception {
        ensureActive();
        long checkpointId = nextCheckpointId.incrementAndGet();
        CheckpointPlan checkpointPlan = calculatePlan(checkpointId);
        checkpointDeadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        boolean coordinatorFrozen = false;
        List<SourceOperatorStreamTask<Object, SourceSplit>> paused = new ArrayList<>();
        Map<CheckpointSnapshot.OperatorSubtask, CompletableFuture<OneInputStreamTask.CheckpointState>>
                acknowledgements = new LinkedHashMap<>();
        boolean stored = false;
        Throwable originalFailure = null;
        // A decline in an upstream Operator must wake a checkpoint currently awaiting the Sink.
        CompletableFuture<Void> firstFailedAcknowledgement = new CompletableFuture<>();
        try {
            await(coordinator.pauseForCheckpoint());
            coordinatorFrozen = true;
            waitForAssignments();
            // Register the running participants before injecting any trigger barrier.
            for (OneInputStreamTask task : inputTasks) {
                CheckpointSnapshot.OperatorSubtask id = task.checkpointIdentity();
                if (!checkpointPlan.tasksToAcknowledge().contains(id)) {
                    continue;
                }
                CompletableFuture<OneInputStreamTask.CheckpointState> acknowledgement =
                        task.expectCheckpoint(checkpointId);
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
            String sourceUid =
                    jobGraph.getVertices().getFirst().getHeadOperator().getUid();
            for (SourceOperatorStreamTask<Object, SourceSplit> sourceTask : sourceTasks) {
                CheckpointSnapshot.OperatorSubtask id = new CheckpointSnapshot.OperatorSubtask(
                        sourceUid, sourceTask.taskInfo().subtaskIndex());
                if (!checkpointPlan.triggerTasks().contains(id)) {
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
            }
            SourceCoordinatorCheckpoint<SourceSplit, Object> sourceState =
                    await(coordinator.snapshotCoordinator(checkpointId));
            // The Reader and Enumerator are paused *now*. Serialize their potentially mutable
            // Split/Enumerator objects before either one is allowed to resume. Downstream barrier
            // alignment may take time and must never change the recorded source offset.
            CheckpointSnapshot frozenSource = serialize(checkpointId, sourceState, readerStates, Map.of());
            for (OneInputStreamTask task : inputTasks) {
                if (checkpointPlan.triggerTasks().contains(task.checkpointIdentity())) {
                    await(task.triggerCheckpoint(checkpointId));
                }
            }

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
                if (state.finished()) {
                    finishedReports.add(entry.getKey());
                } else if (!state.operatorStates().isEmpty()) {
                    operatorStates.put(entry.getKey(), state.operatorStates());
                }
            }
            Set<CheckpointSnapshot.OperatorSubtask> finishedSubtasks =
                    checkpointPlan.finishedSubtasks(checkpointId, finishedReports, operatorStates);
            CheckpointSnapshot snapshot = new CheckpointSnapshot(
                    checkpointId,
                    graphSignature,
                    frozenSource.enumeratorState(),
                    frozenSource.readerSplits(),
                    frozenSource.assignments(),
                    System.currentTimeMillis(),
                    operatorStates,
                    checkpointPlan.fullyFinishedOperators(),
                    finishedSubtasks);
            // A published Checkpoint must be recoverable. PR3 will install finished-on-restore
            // before enabling publication of checkpoints containing finished-task markers.
            if (snapshot.hasFinishedOperators()) {
                throw new CheckpointDeclinedException(
                        checkpointId, "Finished checkpoint restore is not implemented yet");
            }
            storage.save(snapshot);
            stored = true;
            published.complete(snapshot);
            if (System.nanoTime() >= checkpointDeadlineNanos) {
                throw new IllegalStateException("Checkpoint persisted after deadline; refusing completion callback");
            }
            await(coordinator.notifyCheckpointComplete(checkpointId));
            for (SourceOperatorStreamTask<Object, SourceSplit> sourceTask : sourceTasks) {
                if (!sourceTask.completionFuture().isDone()) {
                    await(sourceTask.notifyCheckpointComplete(checkpointId));
                }
            }
        } catch (Exception | Error failure) {
            originalFailure = failure;
            throw failure;
        } finally {
            if (!stored) {
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
            SourceCoordinatorCheckpoint<SourceSplit, Object> sourceState,
            Map<Integer, List<SourceSplit>> readerStates,
            Map<CheckpointSnapshot.OperatorSubtask, Map<String, CheckpointSnapshot.SerializedState>> operatorStates)
            throws Exception {
        SimpleVersionedSerializer<SourceSplit> splits = source.getSplitSerializer();
        SimpleVersionedSerializer<Object> enumerator = source.getEnumeratorCheckpointSerializer();
        var state = new CheckpointSnapshot.SerializedState(
                enumerator.getVersion(), enumerator.serialize(sourceState.enumeratorState()));
        return new CheckpointSnapshot(
                checkpointId,
                graphSignature,
                state,
                serializeSplits(readerStates, splits),
                serializeSplits(sourceState.assignedSinceLastCompletedCheckpoint(), splits),
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
            } catch (IllegalStateException pending) {
                if (!pending.getMessage().contains("尚未确认") || System.nanoTime() >= checkpointDeadlineNanos) {
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
            throw new CancellationException("Checkpoint 已取消");
        }
    }

    private <T> T await(CompletableFuture<T> future) throws Exception {
        try {
            long remainingNanos = checkpointDeadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new IllegalStateException("整个 Checkpoint 已经超过超时时间");
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
            throw new IllegalStateException("Checkpoint 等待 Task/Coordinator 响应超时", failure);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            timer.shutdownNow();
        }
    }
}
