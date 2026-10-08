package io.yak.ops.flow.runtime.source.coordinator;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.flow.runtime.checkpoint.SourceCoordinatorCheckpoint;
import io.yak.ops.flow.runtime.source.event.ReaderRegistrationEvent;
import io.yak.ops.flow.runtime.source.event.RequestSplitEvent;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
 * <p>当前仅支持整个作业失败后统一恢复，不实现单 Reader 局部故障恢复。
 * Coordinator Checkpoint 只是协调侧片段，不能替代完整作业 Checkpoint。
 */
public final class SourceCoordinator<T, SplitT extends SourceSplit, EnumStateT> implements AutoCloseable {

    private final Source<T, SplitT, EnumStateT> source;
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

    public SourceCoordinator(Source<T, SplitT, EnumStateT> source, int parallelism) {
        this(source, parallelism, null, false);
    }

    /** 从已完成的全局检查点恢复时提供的 Enumerator 状态；Reader 状态另行恢复。 */
    public static <T, SplitT extends SourceSplit, EnumStateT>
            SourceCoordinator<T, SplitT, EnumStateT> restore(
                    Source<T, SplitT, EnumStateT> source, int parallelism, EnumStateT state) {
        return new SourceCoordinator<>(source, parallelism,
                Objects.requireNonNull(state, "恢复状态不能为空"), true);
    }

    private SourceCoordinator(Source<T, SplitT, EnumStateT> source, int parallelism,
            EnumStateT restoredEnumeratorState, boolean restoring) {
        this.source = Objects.requireNonNull(source, "source 不能为空");
        if (parallelism <= 0) {
            throw new IllegalArgumentException("并行度必须大于 0");
        }
        this.restoredEnumeratorState = restoredEnumeratorState;
        this.restoring = restoring;
        this.context = new SourceCoordinatorContext<>(parallelism, eventLoop, discovery,
                () -> eventThread, this::fail);
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

    /** Reader 已创建 Gateway 后进行注册；不会等待分片实际读取完成。 */
    public CompletableFuture<Void> registerReader(int subtaskId, SourceReaderGateway<SplitT> gateway) {
        ReaderRegistrationEvent event = new ReaderRegistrationEvent(subtaskId);
        Objects.requireNonNull(gateway, "gateway 不能为空");
        return submit(() -> {
            ensureStarted();
            context.registerReader(event.subtaskId(), gateway);
            try {
                enumerator.addReader(event.subtaskId());
                return null;
            } catch (Throwable failure) {
                fail(failure);
                throw failure;
            }
        });
    }

    /** 将 Reader 发出的请求送入 Enumerator 所在事件循环。 */
    public CompletableFuture<Void> requestSplit(int subtaskId) {
        RequestSplitEvent event = new RequestSplitEvent(subtaskId);
        return submit(() -> {
            ensureStarted();
            if (!context.canRequestSplit(event.subtaskId())) {
                return null;
            }
            try {
                enumerator.handleSplitRequest(event.subtaskId());
                return null;
            } catch (Throwable failure) {
                fail(failure);
                throw failure;
            }
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
    public CompletableFuture<Void> readerFailed(int subtaskId, Throwable failure) {
        Objects.requireNonNull(failure, "failure 不能为空");
        return submit(() -> {
            context.checkRegistered(subtaskId);
            fail(new IllegalStateException("Source Reader 故障，作业需要整体恢复：" + subtaskId, failure));
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
