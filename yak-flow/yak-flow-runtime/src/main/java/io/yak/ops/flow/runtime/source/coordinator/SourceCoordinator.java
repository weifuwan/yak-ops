package io.yak.ops.flow.runtime.source.coordinator;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * 单个 Source 节点在本地 Runtime 中的枚举和分片分配协调器。
 *
 * <p>Coordinator 只负责 Enumerator 与 Reader Task 的控制面：注册、请求、分片交付、
 * 异步发现、快照与退出。不轮询 SourceReader，也不负责记录通道及 Sink 写入。
 *
 * <p>Enumerator 的生命周期回调、Context 状态修改和异步发现的回调都在同一
 * 协调线程串行执行。真正的 Reader Task 在其专属线程消费分片事件。
 *
 * <p>V1 的 Enumerator 快照只负责枚举状态。Runtime 的 Checkpoint 协调器还必须
 * 同时协调 Reader 状态和数据通道；单独调用 snapshotEnumerator 不构成完整 Checkpoint。
 *
 * @param <T> Source 输出的数据类型
 * @param <SplitT> 分片类型
 * @param <EnumStateT> Enumerator 状态类型
 * @author weifuwan
 */
public final class SourceCoordinator<T, SplitT extends SourceSplit, EnumStateT>
        implements SplitEnumeratorContext<SplitT>, AutoCloseable {

    private final Source<T, SplitT, EnumStateT> source;
    private final int parallelism;
    private final EnumStateT restoredState;
    private final boolean restoring;
    private final ExecutorService coordinatorExecutor;
    private final ExecutorService discoveryExecutor;
    private final AtomicBoolean startRequested = new AtomicBoolean();
    private final AtomicBoolean shutdownRequested = new AtomicBoolean();
    private final CompletableFuture<Void> termination = new CompletableFuture<>();

    // 以下可变状态只能在协调器线程读写。
    private final Map<Integer, SourceReaderGateway<SplitT>> readers = new HashMap<>();
    private final Map<String, Integer> pendingDeliveries = new HashMap<>();
    private final Set<Integer> noMoreRequested = new HashSet<>();
    private final Set<Integer> noMoreSent = new HashSet<>();
    private final Set<Integer> pendingNoMore = new HashSet<>();
    private SplitEnumerator<SplitT, EnumStateT> enumerator;
    private volatile Thread coordinatorThread;

    /** 创建一次全新 Source 执行的协调器。 */
    public SourceCoordinator(Source<T, SplitT, EnumStateT> source, int parallelism) {
        this(source, parallelism, null, false);
    }

    /** 从一个已经完成并被一致持久化的 Checkpoint 中的 Enumerator 状态创建协调器。 */
    public static <T, SplitT extends SourceSplit, EnumStateT>
            SourceCoordinator<T, SplitT, EnumStateT> restore(
                    Source<T, SplitT, EnumStateT> source, int parallelism, EnumStateT restoredState) {
        return new SourceCoordinator<>(source, parallelism,
                Objects.requireNonNull(restoredState, "恢复的枚举状态不能为空"), true);
    }

    private SourceCoordinator(Source<T, SplitT, EnumStateT> source,
            int parallelism, EnumStateT restoredState, boolean restoring) {
        this.source = Objects.requireNonNull(source, "source 不能为空");
        if (parallelism <= 0) {
            throw new IllegalArgumentException("Source 并行度必须大于 0");
        }
        this.parallelism = parallelism;
        this.restoredState = restoredState;
        this.restoring = restoring;
        this.coordinatorExecutor = Executors.newSingleThreadExecutor(
                Thread.ofVirtual().name("yak-source-coordinator-", 0).factory());
        this.discoveryExecutor = Executors.newSingleThreadExecutor(
                Thread.ofVirtual().name("yak-source-discovery-", 0).factory());
    }

    /**
     * 创建并启动 Enumerator；成功表示 Enumerator 已启动，不表示任何 Reader 已准备就绪。
     * 同一个 Coordinator 只允许启动一次。
     */
    public CompletableFuture<Void> start() {
        if (!startRequested.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("SourceCoordinator 已经启动"));
        }
        return submit(() -> {
            try {
                enumerator = Objects.requireNonNull(
                        restoring ? source.restoreEnumerator(this, restoredState)
                                : source.createEnumerator(this), "Source 返回了空 Enumerator");
                enumerator.start();
                return null;
            } catch (Exception | Error failure) {
                fail(failure);
                throw failure;
            }
        });
    }

    /**
     * 注册一个已由 Runtime 创建的 Reader Task，随后通知 Connector 的 Enumerator。
     * Gateway 自行负责将消息串行投递到实际 Reader Task 线程。
     */
    public CompletableFuture<Void> registerReader(int subtaskId, SourceReaderGateway<SplitT> gateway) {
        checkSubtask(subtaskId);
        Objects.requireNonNull(gateway, "gateway 不能为空");
        return submit(() -> {
            ensureStarted();
            if (readers.putIfAbsent(subtaskId, gateway) != null) {
                throw new IllegalArgumentException("Reader 重复注册：" + subtaskId);
            }
            try {
                enumerator.addReader(subtaskId);
            } catch (Exception | Error failure) {
                fail(failure);
                throw failure;
            }
            return null;
        });
    }

    /** 把 Reader 的请求送到 Connector Enumerator，并在协调线程上处理。 */
    public CompletableFuture<Void> requestSplit(int subtaskId) {
        checkSubtask(subtaskId);
        return submit(() -> {
            ensureStarted();
            if (!readers.containsKey(subtaskId)) {
                throw new IllegalArgumentException("Reader 尚未注册：" + subtaskId);
            }
            if (noMoreRequested.contains(subtaskId)) {
                throw new IllegalStateException("Reader 已被通知不会再收到 Split：" + subtaskId);
            }
            try {
                enumerator.handleSplitRequest(subtaskId);
            } catch (Exception | Error failure) {
                fail(failure);
                throw failure;
            }
            return null;
        });
    }

    /**
     * 仅保存 Enumerator 的枚举状态。交付中的 Split 或 NoMoreSplits 尚未确认时拒绝快照。
     *
     * <p>这份快照不能单独用于恢复整个 Source。Reader 的 Split 进度和 Channel 状态
     * 还需要由上层 CheckpointCoordinator 按一致性屏障协调。
     */
    public CompletableFuture<EnumStateT> snapshotEnumerator(long checkpointId) {
        if (checkpointId < 0) {
            throw new IllegalArgumentException("checkpointId 不能为负数");
        }
        return submit(() -> {
            ensureStarted();
            if (!pendingDeliveries.isEmpty() || !pendingNoMore.isEmpty()) {
                throw new IllegalStateException("存在未确认的 Reader 投递，暂不能生成 Enumerator 快照");
            }
            try {
                return Objects.requireNonNull(enumerator.snapshotState(checkpointId), "Enumerator 快照不能为空");
            } catch (Exception | Error failure) {
                fail(failure);
                throw failure;
            }
        });
    }

    /** 完整 Checkpoint 已成功持久化后，由上层 CheckpointCoordinator 调用。 */
    public CompletableFuture<Void> notifyCheckpointComplete(long checkpointId) {
        if (checkpointId < 0) {
            throw new IllegalArgumentException("checkpointId 不能为负数");
        }
        return submit(() -> {
            ensureStarted();
            try {
                enumerator.notifyCheckpointComplete(checkpointId);
            } catch (Exception | Error failure) {
                fail(failure);
                throw failure;
            }
            return null;
        });
    }

    /**
     * Reader 故障后停止本 Source 执行，交由 Job Runtime 从最近完成的 Checkpoint 整体恢复。
     *
     * <p>Coordinator 无法独自知道 Reader 已经消费到哪里，不能将此前已经交付的
     * Split 原样 addSplitsBack 后宣称安全续传。
     */
    public CompletableFuture<Void> readerFailed(int subtaskId, Throwable reason) {
        checkSubtask(subtaskId);
        Objects.requireNonNull(reason, "reason 不能为空");
        return submit(() -> {
            if (!readers.containsKey(subtaskId)) {
                throw new IllegalArgumentException("Reader 尚未注册：" + subtaskId);
            }
            fail(new IllegalStateException("Reader 故障，需要由 Runtime 执行整体恢复：" + subtaskId, reason));
            return null;
        });
    }

    /** 返回协调器的退出 Future；出现运行时故障时异常完成。 */
    public CompletableFuture<Void> terminationFuture() {
        return termination;
    }

    @Override
    public int currentParallelism() {
        return parallelism;
    }

    @Override
    public Set<Integer> registeredReaders() {
        assertCoordinatorThread();
        return Set.copyOf(readers.keySet());
    }

    /**
     * 由 Enumerator 在协调线程内调用：异步交付 Split，并等待 Reader Task 确认处理。
     *
     * <p>交付失败会让 Source 执行失败。不能静默重试，否则可能把同一 Split
     * 同时交给旧 Reader 与新 Reader。
     */
    @Override
    public void assignSplit(SplitT split, int subtaskId) {
        assertCoordinatorThread();
        ensureStarted();
        Objects.requireNonNull(split, "split 不能为空");
        checkSubtask(subtaskId);
        SourceReaderGateway<SplitT> gateway = requireReader(subtaskId);
        if (noMoreRequested.contains(subtaskId)) {
            throw new IllegalStateException("已经声明不会再向 Reader 分配 Split：" + subtaskId);
        }
        String splitId = split.splitId();
        if (splitId == null || splitId.isBlank()) {
            throw new IllegalArgumentException("Split ID 不能为空");
        }
        if (pendingDeliveries.putIfAbsent(splitId, subtaskId) != null) {
            throw new IllegalArgumentException("Split 正在交付中，不允许重复分配：" + splitId);
        }
        try {
            CompletionStage<Void> delivered = Objects.requireNonNull(
                    gateway.addSplits(List.of(split)), "ReaderGateway 不能返回 null");
            delivered.whenComplete((unused, failure) -> postInternal(() -> {
                if (failure != null) {
                    fail(new IllegalStateException("Split 交付失败：" + splitId, failure));
                    return;
                }
                pendingDeliveries.remove(splitId);
                maybeSignalNoMore(subtaskId);
            }));
        } catch (RuntimeException | Error failure) {
            fail(failure);
            throw failure;
        }
    }

    /** 在全部已发 Split 被确认交付之后，才通知 Reader 不再接收新 Split。 */
    @Override
    public void signalNoMoreSplits(int subtaskId) {
        assertCoordinatorThread();
        checkSubtask(subtaskId);
        requireReader(subtaskId);
        noMoreRequested.add(subtaskId);
        maybeSignalNoMore(subtaskId);
    }

    /**
     * 在专属后台线程处理可能阻塞的发现操作，结果切回协调器线程。
     * action 不能直接修改 Enumerator 的状态。
     */
    @Override
    public <A> void callAsync(Callable<A> action, BiConsumer<A, Throwable> handler) {
        assertCoordinatorThread();
        Objects.requireNonNull(action, "action 不能为空");
        Objects.requireNonNull(handler, "handler 不能为空");
        discoveryExecutor.execute(() -> {
            A value = null;
            Throwable failure = null;
            try {
                value = action.call();
            } catch (Throwable error) {
                failure = error;
            }
            A completedValue = value;
            Throwable completedFailure = failure;
            postInternal(() -> handler.accept(completedValue, completedFailure));
        });
    }

    @Override
    public void runInCoordinatorThread(Runnable action) {
        Objects.requireNonNull(action, "action 不能为空");
        if (Thread.currentThread() == coordinatorThread) {
            action.run();
        } else {
            postInternal(action);
        }
    }

    /** 异步释放 Enumerator 和协调器资源；完成并不保证 Reader Task 已退出。 */
    public CompletableFuture<Void> closeAsync() {
        if (shutdownRequested.compareAndSet(false, true)) {
            try {
                coordinatorExecutor.execute(() -> {
                    coordinatorThread = Thread.currentThread();
                    cleanup(null);
                });
            } catch (RejectedExecutionException error) {
                cleanup(error);
            }
        }
        return termination;
    }

    @Override
    public void close() throws Exception {
        if (Thread.currentThread() == coordinatorThread) {
            closeAsync();
            return;
        }
        try {
            closeAsync().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (ExecutionException failure) {
            throw new Exception("SourceCoordinator 关闭失败", failure.getCause());
        }
    }

    private void maybeSignalNoMore(int subtaskId) {
        if (!noMoreRequested.contains(subtaskId) || noMoreSent.contains(subtaskId)) {
            return;
        }
        if (pendingDeliveries.containsValue(subtaskId)) {
            return;
        }
        noMoreSent.add(subtaskId);
        pendingNoMore.add(subtaskId);
        try {
            CompletionStage<Void> stage = Objects.requireNonNull(
                    requireReader(subtaskId).noMoreSplits(), "ReaderGateway 不能返回 null");
            stage.whenComplete((unused, failure) -> postInternal(() -> {
                if (failure != null) {
                    fail(new IllegalStateException("NoMoreSplits 投递失败：" + subtaskId, failure));
                } else {
                    pendingNoMore.remove(subtaskId);
                }
            }));
        } catch (RuntimeException | Error failure) {
            fail(failure);
            throw failure;
        }
    }

    private SourceReaderGateway<SplitT> requireReader(int subtaskId) {
        SourceReaderGateway<SplitT> gateway = readers.get(subtaskId);
        if (gateway == null) {
            throw new IllegalArgumentException("Reader 尚未注册：" + subtaskId);
        }
        return gateway;
    }

    private void checkSubtask(int subtaskId) {
        if (subtaskId < 0 || subtaskId >= parallelism) {
            throw new IllegalArgumentException("Source 子任务编号超出范围：" + subtaskId);
        }
    }

    private void ensureStarted() {
        if (enumerator == null) {
            throw new IllegalStateException("Enumerator 尚未创建或启动");
        }
    }

    private void assertCoordinatorThread() {
        if (Thread.currentThread() != coordinatorThread) {
            throw new IllegalStateException("该方法只允许在 SourceCoordinator 协调线程上调用");
        }
    }

    private <R> CompletableFuture<R> submit(Callable<R> action) {
        if (shutdownRequested.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("SourceCoordinator 已关闭"));
        }
        CompletableFuture<R> result = new CompletableFuture<>();
        try {
            coordinatorExecutor.execute(() -> {
                coordinatorThread = Thread.currentThread();
                if (shutdownRequested.get()) {
                    result.completeExceptionally(new IllegalStateException("SourceCoordinator 已关闭"));
                    return;
                }
                try {
                    result.complete(action.call());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    private void postInternal(Runnable action) {
        if (shutdownRequested.get()) {
            return;
        }
        try {
            coordinatorExecutor.execute(() -> {
                coordinatorThread = Thread.currentThread();
                if (shutdownRequested.get()) {
                    return;
                }
                try {
                    action.run();
                } catch (Throwable failure) {
                    fail(failure);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // 协调器已开始关闭时，迟到的异步回调不再修改运行状态。
        }
    }

    private void fail(Throwable cause) {
        if (shutdownRequested.compareAndSet(false, true)) {
            cleanup(cause);
        }
    }

    private void cleanup(Throwable failure) {
        Throwable error = failure;
        try {
            if (enumerator != null) {
                enumerator.close();
            }
        } catch (Throwable closeFailure) {
            if (error == null) {
                error = closeFailure;
            } else if (error != closeFailure) {
                error.addSuppressed(closeFailure);
            }
        } finally {
            discoveryExecutor.shutdownNow();
            coordinatorExecutor.shutdown();
            readers.clear();
            pendingDeliveries.clear();
            noMoreRequested.clear();
            noMoreSent.clear();
            pendingNoMore.clear();
        }
        if (error == null) {
            termination.complete(null);
        } else {
            termination.completeExceptionally(error);
        }
    }
}
