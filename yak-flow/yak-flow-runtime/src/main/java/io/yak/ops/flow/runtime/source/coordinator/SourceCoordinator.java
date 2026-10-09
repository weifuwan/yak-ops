package io.yak.ops.flow.runtime.source.coordinator;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.flow.runtime.checkpoint.SourceCoordinatorCheckpoint;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.operators.coordination.OperatorCoordinatorContext;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;
import io.yak.ops.flow.runtime.operators.coordination.SubtaskGateway;
import io.yak.ops.flow.runtime.source.event.ReaderRegistrationEvent;
import io.yak.ops.flow.runtime.source.event.RequestSplitEvent;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 运行在单独事件循环中的 Source 协调器。
 *
 * <p>只管理 Enumerator 生命周期、Reader 注册和分片请求。
 * Split 投递、分配历史与异步发现由 SourceCoordinatorContext 承担。
 * 不读取记录，不实现 Connector 特有的分片算法。
 *
 * <p>当前仅支持整个作业失败后统一恢复，单个 Reader 的新 Attempt 不可直接取代已注册 Gateway。
 * Coordinator Checkpoint 只是协调侧片段，不能替代完整作业 Checkpoint。
 */
public final class SourceCoordinator<SplitT extends SourceSplit, EnumStateT> implements AutoCloseable {

    private final Source<?, SplitT, EnumStateT> source;
    private final OperatorCoordinatorContext coordinatorContext;
    private final EnumStateT restoredEnumeratorState;
    private final boolean restoring;
    private final ExecutorService eventLoop = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("yak-source-coordinator-", 0).factory());
    private final ExecutorService discovery = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("yak-source-discovery-", 0).factory());
    private final SourceCoordinatorContext<SplitT> context;
    private final AtomicBoolean startRequested = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private volatile Thread eventThread;
    private SplitEnumerator<SplitT, EnumStateT> enumerator;
    private boolean started;
    private boolean checkpointPaused;
    private final Set<Integer> deferredSplitRequests = new LinkedHashSet<>();

    public SourceCoordinator(Source<?, SplitT, EnumStateT> source, OperatorCoordinatorContext coordinatorContext) {
        this(source, coordinatorContext, null, false);
    }

    /** 从全局已完成 Checkpoint 的 Enumerator 状态恢复；Reader 状态由上层另外恢复。 */
    public static <SplitT extends SourceSplit, EnumStateT> SourceCoordinator<SplitT, EnumStateT> restore(
            Source<?, SplitT, EnumStateT> source, OperatorCoordinatorContext coordinatorContext, EnumStateT state) {
        return new SourceCoordinator<>(source, coordinatorContext,
                Objects.requireNonNull(state, "恢复状态不能为空"), true);
    }

    private SourceCoordinator(Source<?, SplitT, EnumStateT> source,
            OperatorCoordinatorContext coordinatorContext, EnumStateT restoredEnumeratorState, boolean restoring) {
        this.source = Objects.requireNonNull(source, "source 不能为空");
        this.coordinatorContext = Objects.requireNonNull(coordinatorContext, "coordinatorContext 不能为空");
        this.restoredEnumeratorState = restoredEnumeratorState;
        this.restoring = restoring;
        this.context = new SourceCoordinatorContext<>(coordinatorContext, eventLoop, discovery,
                () -> eventThread, this::fail);
    }

    /** Source 所属的不可变运行身份；用于 Task 装配时检查 Job/Operator/Parallelism。 */
    public OperatorCoordinatorContext coordinatorContext() {
        return coordinatorContext;
    }

    /** 创建 Enumerator 并调用 start()；同一个 Coordinator 不可重复启动。 */
    public CompletableFuture<Void> start() {
        if (!startRequested.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("SourceCoordinator 已启动"));
        }
        return submit(() -> {
            try {
                enumerator = Objects.requireNonNull(restoring
                        ? source.restoreEnumerator(context, restoredEnumeratorState)
                        : source.createEnumerator(context), "Source 返回了空 Enumerator");
                enumerator.start();
                started = true;
                return null;
            } catch (Throwable failure) {
                fail(failure);
                throw failure;
            }
        });
    }

    /** Reader 已创建 SubtaskGateway 后进行注册；不会等待分片实际读取完成。 */
    public CompletableFuture<Void> registerReader(RuntimeTaskInfo taskInfo, SubtaskGateway gateway) {
        Objects.requireNonNull(gateway, "gateway 不能为空");
        try {
            coordinatorContext.validateTask(taskInfo);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        ReaderRegistrationEvent event =
                new ReaderRegistrationEvent(taskInfo.subtaskIndex(), taskInfo.attemptNumber());
        return submit(() -> {
            ensureStarted();
            context.registerReader(taskInfo, gateway);
            try {
                enumerator.addReader(event.subtaskId());
                return null;
            } catch (Throwable failure) {
                fail(failure);
                throw failure;
            }
        });
    }

    /**
     * 接收指定 SourceOperator 子任务发来的控制事件。
     *
     * <p>目前只支持 RequestSplitEvent。事件中的 Subtask 与 Attempt 身份必须匹配
     * 已注册的 RuntimeTaskInfo，拒绝过期 Reader 请求；事件在协调器线程中处理。
     */
    public CompletableFuture<Void> handleEventFromOperator(RuntimeTaskInfo taskInfo, OperatorEvent event) {
        Objects.requireNonNull(event, "event 不能为空");
        try {
            coordinatorContext.validateTask(taskInfo);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        if (!(event instanceof RequestSplitEvent request)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Coordinator 不支持的 OperatorEvent："
                            + event.getClass().getName()));
        }
        if (taskInfo.subtaskIndex() != request.subtaskId()
                || taskInfo.attemptNumber() != request.attemptNumber()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "OperatorEvent 的 Subtask / Attempt 与发送者身份不匹配"));
        }
        return submit(() -> {
            ensureStarted();
            if (!context.canRequestSplit(taskInfo)) {
                // 已发送 NoMoreSplits 的迟到请求不应再次触发 Enumerator。
                return null;
            }
            if (checkpointPaused) {
                deferredSplitRequests.add(taskInfo.subtaskIndex());
                return null;
            }
            try {
                enumerator.handleSplitRequest(taskInfo.subtaskIndex());
                return null;
            } catch (Throwable failure) {
                fail(failure);
                throw failure;
            }
        });
    }

    /** Source 协调侧冻结新 Split 请求；现有 Gateway 确认和失败仍正常处理。 */
    public CompletableFuture<Void> pauseForCheckpoint() {
        return submit(() -> {
            ensureStarted();
            if (checkpointPaused) {
                throw new IllegalStateException("SourceCoordinator 已暂停");
            }
            checkpointPaused = true;
            context.pauseForCheckpoint();
            return null;
        });
    }

    /** 检查全部在途 Split 和 NoMoreSplits 事件均已由 Reader Mailbox 处理。 */
    public CompletableFuture<Void> awaitCheckpointDeliveries() {
        return submit(() -> {
            ensureStarted();
            context.ensureDeliveriesCompleted();
            return null;
        });
    }

    /** Checkpoint 成功或失败都解冻 Reader Split 请求和 Enumerator 后台回调。 */
    public CompletableFuture<Void> resumeAfterCheckpoint() {
        return submit(() -> {
            if (checkpointPaused) {
                checkpointPaused = false;
                context.resumeAfterCheckpoint();
                for (Integer subtask : deferredSplitRequests) {
                    if (context.registeredReaders().contains(subtask)) {
                        enumerator.handleSplitRequest(subtask);
                    }
                }
                deferredSplitRequests.clear();
            }
            return null;
        });
    }

    /** 获取 Enumerator 的局部状态快照，不构成完整 Checkpoint。 */
    public CompletableFuture<EnumStateT> snapshotEnumerator(long checkpointId) {
        validateCheckpointId(checkpointId);
        return submit(() -> {
            ensureStarted();
            context.ensureDeliveriesCompleted();
            return Objects.requireNonNull(enumerator.snapshotState(checkpointId), "Enumerator 快照不能为空");
        });
    }

    /**
     * 获取协调侧完整快照片段，包括 Enumerator 与未被成功确认的分片分配。
     * 只有上层协调了 Reader/Channel/Sink 的同一 Checkpoint，并持久化成功才可提交确认。
     */
    public CompletableFuture<SourceCoordinatorCheckpoint<SplitT, EnumStateT>> snapshotCoordinator(
            long checkpointId) {
        validateCheckpointId(checkpointId);
        return submit(() -> {
            ensureStarted();
            context.ensureDeliveriesCompleted();
            EnumStateT state = Objects.requireNonNull(
                    enumerator.snapshotState(checkpointId), "Enumerator 快照不能为空");
            Map<Integer, java.util.List<SplitT>> assignments =
                    context.assignmentTracker().snapshot(checkpointId);
            return new SourceCoordinatorCheckpoint<>(checkpointId, state, assignments);
        });
    }

    /** 完整作业 Checkpoint 成功后才能调用。 */
    public CompletableFuture<Void> notifyCheckpointComplete(long checkpointId) {
        validateCheckpointId(checkpointId);
        return submit(() -> {
            ensureStarted();
            enumerator.notifyCheckpointComplete(checkpointId);
            context.assignmentTracker().notifyCheckpointComplete(checkpointId);
            return null;
        });
    }

    /** 清除失败的快照尝试，不丢弃分片分配历史。 */
    public CompletableFuture<Void> notifyCheckpointAborted(long checkpointId) {
        validateCheckpointId(checkpointId);
        return submit(() -> {
            context.assignmentTracker().notifyCheckpointAborted(checkpointId);
            return null;
        });
    }

    /** Reader 异常由 Job Runtime 执行整体恢复；此处不会盲目重新分配原始 Split。 */
    public CompletableFuture<Void> readerFailed(RuntimeTaskInfo taskInfo, Throwable failure) {
        Objects.requireNonNull(failure, "failure 不能为空");
        return submit(() -> {
            context.checkRegistered(taskInfo);
            fail(new IllegalStateException("Source Reader 故障，作业需要整体恢复："
                    + taskInfo.subtaskIndex() + "，attempt=" + taskInfo.attemptNumber(), failure));
            return null;
        });
    }

    public CompletableFuture<Void> terminationFuture() {
        return termination.copy();
    }

    public CompletableFuture<Void> closeAsync() {
        if (closing.compareAndSet(false, true)) {
            try {
                eventLoop.execute(() -> {
                    eventThread = Thread.currentThread();
                    cleanup(null);
                });
            } catch (RejectedExecutionException error) {
                cleanup(error);
            }
        }
        return termination.copy();
    }

    @Override
    public void close() throws Exception {
        if (Thread.currentThread() == eventThread) {
            closeAsync();
            return;
        }
        try {
            closeAsync().get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw failure;
        } catch (ExecutionException failure) {
            throw new Exception("SourceCoordinator 关闭失败", failure.getCause());
        }
    }

    private void ensureStarted() {
        if (!started || enumerator == null) {
            throw new IllegalStateException("Enumerator 尚未启动");
        }
    }

    private static void validateCheckpointId(long checkpointId) {
        if (checkpointId < 0) {
            throw new IllegalArgumentException("checkpointId 不能为负数");
        }
    }

    private <V> CompletableFuture<V> submit(Callable<V> action) {
        if (closing.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("SourceCoordinator 已关闭"));
        }
        CompletableFuture<V> reply = new CompletableFuture<>();
        try {
            eventLoop.execute(() -> {
                eventThread = Thread.currentThread();
                if (closing.get()) {
                    reply.completeExceptionally(new IllegalStateException("SourceCoordinator 已关闭"));
                    return;
                }
                try {
                    reply.complete(action.call());
                } catch (Throwable failure) {
                    reply.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            reply.completeExceptionally(failure);
        }
        return reply;
    }

    private void fail(Throwable failure) {
        if (closing.compareAndSet(false, true)) {
            if (Thread.currentThread() == eventThread) {
                cleanup(failure);
            } else {
                try {
                    eventLoop.execute(() -> {
                        eventThread = Thread.currentThread();
                        cleanup(failure);
                    });
                } catch (RejectedExecutionException error) {
                    failure.addSuppressed(error);
                    cleanup(failure);
                }
            }
        }
    }

    private void cleanup(Throwable original) {
        Throwable failure = original;
        try {
            if (enumerator != null) {
                enumerator.close();
            }
        } catch (Throwable error) {
            if (failure == null) {
                failure = error;
            } else if (failure != error) {
                failure.addSuppressed(error);
            }
        } finally {
            if (Thread.currentThread() == eventThread) {
                context.dispose();
            }
            discovery.shutdownNow();
            eventLoop.shutdown();
        }
        if (failure == null) {
            termination.complete(null);
        } else {
            termination.completeExceptionally(failure);
        }
    }
}
