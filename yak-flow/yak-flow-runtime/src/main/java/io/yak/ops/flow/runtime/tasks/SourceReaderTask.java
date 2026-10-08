package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.flow.runtime.source.coordinator.SourceReaderGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

/**
 * 一个 Source 并行子任务的本地运行容器。
 *
 * <p>Reader 创建、启动、分片交付、轮询、状态快照及关闭都在同一条工作线程上执行；
 * Coordinator 仅通过非阻塞 Gateway 投递控制事件。
 *
 * <p>本类不持有数据缓冲区：记录通过 Runtime 注入的 ReaderOutput 发往下游。
 * 若下游有界 Channel 发生背压，collect() 可以阻塞当前 Reader 工作线程，但必须响应中断。
 * 所有控制事件保留在有界邮箱中，并在当前处理返回后串行执行，不能越过正在输出的数据。
 *
 * <p>Reader 的 pollNext() 必须遵守非阻塞契约。暂时无数据时，通过 isAvailable() 唤醒，
 * 而不是主动 sleep 或忙轮询。本类尚不负责端到端 Checkpoint Barrier 对齐。
 *
 * @param <T> Source 输出的数据类型
 * @param <SplitT> Source Split 类型
 * @author weifuwan
 */
public final class SourceReaderTask<T, SplitT extends SourceSplit>
        implements SourceReaderGateway<SplitT>, SourceReaderContext, AutoCloseable {

    private static final int DEFAULT_MAILBOX_CAPACITY = 128;
    private static final int MAX_COMMANDS_PER_TURN = 32;
    private static final int MAX_SPURIOUS_AVAILABILITY = 64;
    private static final Runnable WAKEUP = () -> {};

    private final Source<T, SplitT, ?> source;
    private final ReaderOutput<T> output;
    private final IntFunction<? extends CompletionStage<Void>> requestSplit;
    private final int subtaskId;
    private final int parallelism;
    private final ArrayBlockingQueue<Runnable> mailbox;
    private final Set<CompletableFuture<?>> pendingReplies = ConcurrentHashMap.newKeySet();
    private final Object lifecycleLock = new Object();
    private final AtomicReference<Throwable> asynchronousFailure = new AtomicReference<>();
    private final AtomicBoolean wakeupQueued = new AtomicBoolean();
    private final CompletableFuture<Void> started = new CompletableFuture<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final CompletableFuture<Void> cancellation = new CompletableFuture<>();

    private volatile Thread worker;
    private volatile boolean cancelRequested;
    private boolean startRequested;
    private boolean noMoreSplitsQueued;
    private boolean noMoreSplitsReceived;
    private SourceReader<T, SplitT> reader;

    /** 使用默认容量为 128 的控制邮箱。 */
    public SourceReaderTask(
            Source<T, SplitT, ?> source,
            int subtaskId,
            int parallelism,
            ReaderOutput<T> output,
            IntFunction<? extends CompletionStage<Void>> requestSplit) {
        this(source, subtaskId, parallelism, output, requestSplit, DEFAULT_MAILBOX_CAPACITY);
    }

    /**
     * @param source Source 组件定义，Reader 将在工作线程中创建
     * @param subtaskId 当前 Source 子任务编号
     * @param parallelism Source 并行度
     * @param output 下游输出端口，发生背压时必须支持中断
     * @param requestSplit 请求转发入口，通常指向 SourceCoordinator.requestSplit(int)
     * @param mailboxCapacity 最大待处理控制事件数
     */
    public SourceReaderTask(
            Source<T, SplitT, ?> source,
            int subtaskId,
            int parallelism,
            ReaderOutput<T> output,
            IntFunction<? extends CompletionStage<Void>> requestSplit,
            int mailboxCapacity) {
        this.source = Objects.requireNonNull(source, "source 不能为空");
        this.output = Objects.requireNonNull(output, "output 不能为空");
        this.requestSplit = Objects.requireNonNull(requestSplit, "requestSplit 不能为空");
        if (parallelism <= 0 || subtaskId < 0 || subtaskId >= parallelism) {
            throw new IllegalArgumentException("Source 子任务编号或并行度无效");
        }
        if (mailboxCapacity <= 0) {
            throw new IllegalArgumentException("控制邮箱容量必须大于 0");
        }
        this.subtaskId = subtaskId;
        this.parallelism = parallelism;
        this.mailbox = new ArrayBlockingQueue<>(mailboxCapacity);
    }

    /**
     * 启动 Reader 的独立工作线程，完成时表示 SourceReader.start() 已正常返回。
     *
     * <p>推荐由 Runtime 先创建 Coordinator、注册 Gateway，再启动 Reader Task，
     * 避免 Reader.start() 中的分片请求先于注册到达 Coordinator。
     */
    public CompletableFuture<Void> start() {
        synchronized (lifecycleLock) {
            if (startRequested) {
                return CompletableFuture.failedFuture(new IllegalStateException("SourceReaderTask 不能重复启动"));
            }
            if (cancelRequested || completion.isDone()) {
                return CompletableFuture.failedFuture(new CancellationException("SourceReaderTask 已取消"));
            }
            startRequested = true;
            Thread thread = Thread.ofVirtual()
                    .name("yak-source-reader-" + subtaskId)
                    .unstarted(this::runReader);
            worker = thread;
            try {
                thread.start();
            } catch (Throwable failure) {
                worker = null;
                started.completeExceptionally(failure);
                completion.completeExceptionally(failure);
            }
        }
        return started.copy();
    }

    /**
     * 返回 Reader 已正常结束且资源已释放后的 Future。
     * 取消或失败时该 Future 异常完成；不代表下游 Sink 已经结束。
     */
    public CompletableFuture<Void> completionFuture() {
        return completion.copy();
    }

    /**
     * 请求取消 Reader。返回的 Future 只在 Reader.close() 之后完成。
     * 若 Reader 无法响应中断，取消不会被虚假标记为已完成。
     */
    public CompletableFuture<Void> cancelAsync() {
        Thread thread;
        synchronized (lifecycleLock) {
            if (cancelRequested) {
                return cancellation.copy();
            }
            if (completion.isDone()) {
                return CompletableFuture.failedFuture(new IllegalStateException("SourceReaderTask 已结束"));
            }
            cancelRequested = true;
            thread = worker;
            if (thread == null) {
                CancellationException canceled = new CancellationException("SourceReaderTask 尚未启动就被取消");
                started.completeExceptionally(canceled);
                completion.completeExceptionally(canceled);
                failPendingReplies(canceled);
                cancellation.complete(null);
            }
        }
        if (thread != null) {
            thread.interrupt();
        }
        return cancellation.copy();
    }

    /** 完成一次分片投递后才确认 Gateway Future。 */
    @Override
    public CompletionStage<Void> addSplits(List<SplitT> splits) {
        Objects.requireNonNull(splits, "splits 不能为空");
        if (splits.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("分片集合不能为空"));
        }
        List<SplitT> copied = List.copyOf(splits);
        for (SplitT split : copied) {
            if (split.splitId() == null || split.splitId().isBlank()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Split ID 不能为空"));
            }
        }
        return enqueue(() -> {
            reader.addSplits(copied);
            return null;
        }, false, true);
    }

    /** 通知 Reader 不会再收到分片；必须排在先前的 addSplits 之后。 */
    @Override
    public CompletionStage<Void> noMoreSplits() {
        return enqueue(() -> {
            reader.notifyNoMoreSplits();
            noMoreSplitsReceived = true;
            return null;
        }, true, true);
    }

    /** 在 Reader 线程中创建读取进度快照，不等于完成全局 Checkpoint。 */
    public CompletableFuture<List<SplitT>> snapshotState(long checkpointId) {
        if (checkpointId < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("checkpointId 不能为负数"));
        }
        CompletableFuture<List<SplitT>> future = new CompletableFuture<>();
        enqueueCommand(() -> {
            try {
                List<SplitT> state = List.copyOf(
                        Objects.requireNonNull(reader.snapshotState(checkpointId), "Reader 快照不能为空"));
                future.complete(state);
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
                throw propagate(failure);
            } finally {
                pendingReplies.remove(future);
            }
        }, future, false, false);
        return future.copy();
    }

    /** 完整 Checkpoint 确认后，在 Reader 线程中执行通知。 */
    public CompletableFuture<Void> notifyCheckpointComplete(long checkpointId) {
        if (checkpointId < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("checkpointId 不能为负数"));
        }
        return enqueue(() -> {
            reader.notifyCheckpointComplete(checkpointId);
            return null;
        }, false, false).toCompletableFuture();
    }

    @Override
    public int getIndexOfSubtask() {
        return subtaskId;
    }

    @Override
    public int currentParallelism() {
        return parallelism;
    }

    /** 转发分片请求，异步失败会使当前 Reader Task 失败。 */
    @Override
    public void sendSplitRequest() {
        if (cancelRequested || completion.isDone()) {
            return;
        }
        try {
            CompletionStage<Void> submitted = Objects.requireNonNull(
                    requestSplit.apply(subtaskId), "Split 请求入口不能返回 null");
            submitted.whenComplete((ignored, failure) -> {
                if (failure != null) {
                    failAsync(failure);
                }
            });
        } catch (Throwable failure) {
            failAsync(failure);
        }
    }

    @Override
    public void close() throws Exception {
        if (completion.isDone()) {
            return;
        }
        if (Thread.currentThread() == worker) {
            cancelAsync();
            return;
        }
        try {
            cancelAsync().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (ExecutionException failure) {
            throw new Exception("SourceReaderTask 关闭失败", failure.getCause());
        } catch (CancellationException canceled) {
            // close() 用于资源释放，取消的状态由 completionFuture() 表达。
        }
    }

    private void runReader() {
        Throwable failure = null;
        boolean inputFinished = false;
        try {
            if (cancelRequested) {
                throw new CancellationException("启动前已取消");
            }
            reader = Objects.requireNonNull(source.createReader(this), "Source 返回了空 Reader");
            if (cancelRequested) {
                throw new CancellationException("启动前已取消");
            }
            reader.start();
            checkStopOrFailure();
            started.complete(null);

            int spuriousAvailability = 0;
            while (true) {
                checkStopOrFailure();
                drainMailbox();
                checkStopOrFailure();

                InputStatus status = Objects.requireNonNull(reader.pollNext(output), "pollNext 不能返回 null");
                switch (status) {
                    case MORE_AVAILABLE -> spuriousAvailability = 0;
                    case END_OF_INPUT -> {
                        if (!noMoreSplitsReceived) {
                            throw new IllegalStateException("Reader 尚未收到 NoMoreSplits 就报告 END_OF_INPUT");
                        }
                        inputFinished = true;
                        return;
                    }
                    case NOTHING_AVAILABLE -> {
                        CompletableFuture<Void> available = Objects.requireNonNull(
                                reader.isAvailable(), "isAvailable 不能返回 null");
                        if (available.isDone()) {
                            // 偶发的空唤醒合法，持续返回已完成 Future 则会造成忙等。
                            available.join();
                            if (++spuriousAvailability >= MAX_SPURIOUS_AVAILABILITY) {
                                throw new IllegalStateException("Reader 连续报告 NOTHING_AVAILABLE，"
                                        + "但 isAvailable 始终已完成，可能造成忙等");
                            }
                            continue;
                        }
                        spuriousAvailability = 0;
                        available.whenComplete((unused, cause) -> {
                            if (cause != null) {
                                failAsync(cause);
                            } else {
                                wakeup();
                            }
                        });
                        executeCommand(mailbox.take());
                    }
                }
            }
        } catch (Throwable cause) {
            failure = cause;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Throwable closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (cancelRequested && (failure == null || failure instanceof InterruptedException
                    || failure instanceof CancellationException)) {
                failure = new CancellationException("SourceReaderTask 已取消");
            }
            if (failure == null && !inputFinished) {
                failure = new IllegalStateException("SourceReaderTask 在未完成输入的情况下退出");
            }
            if (failure == null && asynchronousFailure.get() != null) {
                failure = asynchronousFailure.get();
            }
            if (!started.isDone()) {
                started.completeExceptionally(failure == null
                        ? new IllegalStateException("Reader 未启动") : failure);
            }
            failPendingReplies(failure == null
                    ? new IllegalStateException("SourceReaderTask 已结束") : failure);
            if (failure == null) {
                completion.complete(null);
            } else {
                completion.completeExceptionally(failure);
            }
            if (cancelRequested) {
                if (failure instanceof CancellationException) {
                    cancellation.complete(null);
                } else {
                    cancellation.completeExceptionally(failure);
                }
            }
        }
    }

    private void drainMailbox() {
        for (int i = 0; i < MAX_COMMANDS_PER_TURN; i++) {
            Runnable command = mailbox.poll();
            if (command == null) {
                return;
            }
            executeCommand(command);
        }
    }

    private void executeCommand(Runnable command) {
        if (command == WAKEUP) {
            wakeupQueued.set(false);
        } else {
            command.run();
        }
    }

    private void checkStopOrFailure() {
        if (cancelRequested) {
            throw new CancellationException("SourceReaderTask 已取消");
        }
        Throwable cause = asynchronousFailure.get();
        if (cause != null) {
            throw propagate(cause);
        }
    }

    private CompletionStage<Void> enqueue(Callable<Void> action, boolean noMore, boolean splitControl) {
        CompletableFuture<Void> reply = new CompletableFuture<>();
        enqueueCommand(() -> {
            try {
                action.call();
                reply.complete(null);
            } catch (Throwable cause) {
                reply.completeExceptionally(cause);
                throw propagate(cause);
            } finally {
                pendingReplies.remove(reply);
            }
        }, reply, noMore, splitControl);
        return reply.copy();
    }

    private boolean enqueueCommand(
            Runnable action, CompletableFuture<?> reply, boolean noMore, boolean splitControl) {
        synchronized (lifecycleLock) {
            if (cancelRequested || completion.isDone()) {
                reply.completeExceptionally(new IllegalStateException("SourceReaderTask 已结束或正在取消"));
                return false;
            }
            if (noMoreSplitsQueued && splitControl) {
                reply.completeExceptionally(new IllegalStateException("NoMoreSplits 已安排，不能继续分配 Split"));
                return false;
            }
            pendingReplies.add(reply);
            if (!mailbox.offer(action)) {
                pendingReplies.remove(reply);
                reply.completeExceptionally(new IllegalStateException("SourceReaderTask 控制邮箱已满"));
                return false;
            }
            if (noMore) {
                noMoreSplitsQueued = true;
            }
            return true;
        }
    }

    private void wakeup() {
        if (cancelRequested || completion.isDone()) {
            return;
        }
        if (wakeupQueued.compareAndSet(false, true) && !mailbox.offer(WAKEUP)) {
            // 满邮箱中的控制事件本身就能唤醒工作线程。
            wakeupQueued.set(false);
        }
    }

    private void failAsync(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (asynchronousFailure.compareAndSet(null, cause)) {
            wakeup();
        }
    }

    private void failPendingReplies(Throwable failure) {
        List<CompletableFuture<?>> outstanding = new ArrayList<>(pendingReplies);
        for (CompletableFuture<?> reply : outstanding) {
            reply.completeExceptionally(failure);
            pendingReplies.remove(reply);
        }
        mailbox.clear();
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new CompletionException(failure);
    }

}
