package io.yak.ops.flow.runtime.checkpoint;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.flow.runtime.execution.CompiledJobPlan;
import io.yak.ops.flow.runtime.io.LocalChannel;
import io.yak.ops.flow.runtime.source.coordinator.SourceCoordinator;
import io.yak.ops.flow.runtime.tasks.SinkOperatorStreamTask;
import io.yak.ops.flow.runtime.tasks.SourceOperatorStreamTask;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * 单节点有界 Channel 的对齐式静止切面（quiescent cut）Checkpoint。
 *
 * <p>冻结 Enumerator 新分片请求与发现回调 → 等待在途 Split 处理完成 →
 * 所有 Source Mailbox 暂停并快照 → 逐级等待 Channel 排空以及处理中记录完成 →
 * 每个 Sink Mailbox flush(false) → 持久化版本化状态 → 回调 Checkpoint 完成 →
 * 恢复 Source 与 Enumerator。该协议不提供端到端 exactly-once。
 *
 * <p>中间 Operator 尚未提供状态快照接口，因此启用此协议时仅支持 Source → Sink；
 * 多 Reader 和多 Writer 仍可独立并行。
 */
public final class LocalCheckpointCoordinator implements AutoCloseable {

    private final Source<?, SourceSplit, Object> source;
    private final SourceCoordinator<SourceSplit, Object> coordinator;
    private final List<SourceOperatorStreamTask<Object, SourceSplit>> sourceTasks;
    private final List<List<LocalChannel<Object>>> channelStages;
    private final List<SinkOperatorStreamTask> sinkTasks;
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

    public LocalCheckpointCoordinator(
            CompiledJobPlan plan,
            Source<?, SourceSplit, Object> source,
            SourceCoordinator<SourceSplit, Object> coordinator,
            List<SourceOperatorStreamTask<Object, SourceSplit>> sourceTasks,
            List<List<LocalChannel<Object>>> channelStages,
            List<SinkOperatorStreamTask> sinkTasks,
            FileCheckpointStore storage,
            LocalCheckpointState restored,
            BooleanSupplier cancelled,
            Consumer<Throwable> onFailure) {
        Objects.requireNonNull(plan, "plan 不能为空");
        this.source = Objects.requireNonNull(source, "source 不能为空");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator 不能为空");
        this.sourceTasks = List.copyOf(sourceTasks);
        this.channelStages = channelStages.stream().map(List::copyOf).toList();
        this.sinkTasks = List.copyOf(sinkTasks);
        this.storage = Objects.requireNonNull(storage, "storage 不能为空");
        this.graphSignature = FileCheckpointStore.graphSignature(plan.graph());
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
        if (timeoutMillis <= 0 || sourceTasks.isEmpty() || sinkTasks.isEmpty() || channelStages.isEmpty()) {
            throw new IllegalArgumentException("Checkpoint 超时、Source / Sink / Channel 配置非法");
        }
    }

    /** 周期触发器只运行一个 Checkpoint，前次完成后才调度下一次。 */
    public void start() {
        if (intervalMillis > 0) {
            timer.scheduleWithFixedDelay(() -> {
                if (closed.get() || cancelled.getAsBoolean() || anySourceFinished()) {
                    return;
                }
                try {
                    performCheckpoint();
                } catch (Throwable failure) {
                    if (!closed.get() && !cancelled.getAsBoolean() && !anySourceFinished()) {
                        onFailure.accept(failure);
                    }
                }
            }, intervalMillis, Math.max(intervalMillis, minPauseMillis), TimeUnit.MILLISECONDS);
        }
    }

    /** 手动触发一次完整 Checkpoint；与周期任务使用同一个串行执行器。 */
    public CompletableFuture<LocalCheckpointState> trigger() {
        CompletableFuture<LocalCheckpointState> result = new CompletableFuture<>();
        if (closed.get()) {
            result.completeExceptionally(new IllegalStateException("CheckpointCoordinator 已关闭"));
            return result;
        }
        try {
            timer.execute(() -> {
                try {
                    result.complete(performCheckpoint());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                    if (!closed.get() && !cancelled.getAsBoolean() && !anySourceFinished()) {
                        onFailure.accept(failure);
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    /** 恢复之前的 Split 状态，Reader 已完成的部分可能被协调侧历史重放（at-least-once）。 */
    public static Map<Integer, List<SourceSplit>> restoreSplits(
            LocalCheckpointState snapshot, Source<?, SourceSplit, ?> source) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot 不能为空");
        SimpleVersionedSerializer<SourceSplit> serializer = source.getSplitSerializer();
        Map<Integer, Map<String, SourceSplit>> merged = new LinkedHashMap<>();
        deserializeInto(merged, snapshot.assignments(), serializer);
        // Reader snapshot 带最新的读取进度，覆盖同 splitId 的旧分配快照。
        deserializeInto(merged, snapshot.readerSplits(), serializer);
        Map<Integer, List<SourceSplit>> result = new LinkedHashMap<>();
        merged.forEach((index, splits) -> result.put(index, List.copyOf(splits.values())));
        return Map.copyOf(result);
    }

    public static Object restoreEnumerator(LocalCheckpointState snapshot, Source<?, SourceSplit, Object> source)
            throws IOException {
        var state = snapshot.enumeratorState();
        return source.getEnumeratorCheckpointSerializer().deserialize(state.version(), state.bytes());
    }

    private static void deserializeInto(Map<Integer, Map<String, SourceSplit>> result,
            Map<Integer, List<LocalCheckpointState.SerializedState>> sections,
            SimpleVersionedSerializer<SourceSplit> serializer) throws IOException {
        for (var section : sections.entrySet()) {
            Map<String, SourceSplit> splits = result.computeIfAbsent(section.getKey(), id -> new LinkedHashMap<>());
            for (var state : section.getValue()) {
                SourceSplit split = Objects.requireNonNull(
                        serializer.deserialize(state.version(), state.bytes()), "Source Split 反序列化为空");
                splits.put(split.splitId(), split);
            }
        }
    }

    private LocalCheckpointState performCheckpoint() throws Exception {
        ensureActive();
        long checkpointId = nextCheckpointId.incrementAndGet();
        checkpointDeadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        boolean coordinatorFrozen = false;
        List<SourceOperatorStreamTask<Object, SourceSplit>> paused = new ArrayList<>();
        boolean stored = false;
        Throwable originalFailure = null;
        try {
            await(coordinator.pauseForCheckpoint());
            coordinatorFrozen = true;
            waitForAssignments();
            Map<Integer, List<SourceSplit>> readerStates = new LinkedHashMap<>();
            for (SourceOperatorStreamTask<Object, SourceSplit> sourceTask : sourceTasks) {
                ensureActive();
                List<SourceSplit> splits = await(sourceTask.pauseForCheckpoint(checkpointId));
                paused.add(sourceTask);
                readerStates.put(sourceTask.taskInfo().subtaskIndex(), splits);
            }
            SourceCoordinatorCheckpoint<SourceSplit, Object> sourceState =
                    await(coordinator.snapshotCoordinator(checkpointId));

            // 此时所有 Source Mailbox 均停止发送；按上游到下游的顺序等各阶段完全排空。
            for (List<LocalChannel<Object>> stage : channelStages) {
                ensureActive();
                await(CompletableFuture.allOf(
                        stage.stream().map(LocalChannel::drainedFuture).toArray(CompletableFuture<?>[]::new)));
            }
            for (SinkOperatorStreamTask sinkTask : sinkTasks) {
                ensureActive();
                await(sinkTask.flushForCheckpoint(checkpointId));
            }

            LocalCheckpointState snapshot = serialize(checkpointId, sourceState, readerStates);
            storage.save(snapshot);
            stored = true;
            if (System.nanoTime() >= checkpointDeadlineNanos) {
                throw new IllegalStateException("Checkpoint 状态落盘已超时，拒绝通知 Source 完成");
            }
            await(coordinator.notifyCheckpointComplete(checkpointId));
            for (SourceOperatorStreamTask<Object, SourceSplit> sourceTask : paused) {
                await(sourceTask.notifyCheckpointComplete(checkpointId));
            }
            return snapshot;
        } catch (Exception | Error failure) {
            originalFailure = failure;
            throw failure;
        } finally {
            if (!stored) {
                try {
                    await(coordinator.notifyCheckpointAborted(checkpointId));
                } catch (Exception ignored) {
                    // Coordinator 可能已经因主故障关闭；不覆盖先前异常。
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

    private LocalCheckpointState serialize(long checkpointId,
            SourceCoordinatorCheckpoint<SourceSplit, Object> sourceState,
            Map<Integer, List<SourceSplit>> readerStates) throws Exception {
        SimpleVersionedSerializer<SourceSplit> splits = source.getSplitSerializer();
        SimpleVersionedSerializer<Object> enumerator = source.getEnumeratorCheckpointSerializer();
        var state = new LocalCheckpointState.SerializedState(
                enumerator.getVersion(), enumerator.serialize(sourceState.enumeratorState()));
        return new LocalCheckpointState(
                checkpointId, graphSignature, state,
                serializeSplits(readerStates, splits),
                serializeSplits(sourceState.assignedSinceLastCompletedCheckpoint(), splits),
                System.currentTimeMillis());
    }

    private static Map<Integer, List<LocalCheckpointState.SerializedState>> serializeSplits(
            Map<Integer, List<SourceSplit>> values, SimpleVersionedSerializer<SourceSplit> serializer)
            throws IOException {
        Map<Integer, List<LocalCheckpointState.SerializedState>> result = new LinkedHashMap<>();
        for (var entry : values.entrySet()) {
            List<LocalCheckpointState.SerializedState> states = new ArrayList<>();
            for (SourceSplit split : entry.getValue()) {
                states.add(new LocalCheckpointState.SerializedState(
                        serializer.getVersion(), serializer.serialize(split)));
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

    private boolean anySourceFinished() {
        return sourceTasks.stream().anyMatch(task -> task.completionFuture().isDone());
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
