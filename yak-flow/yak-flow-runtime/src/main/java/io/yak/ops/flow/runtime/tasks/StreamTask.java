package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * 本地 StreamTask 的通用执行线程和 Mailbox 基础。
 *
 * <p>使用单条虚拟线程串行处理控制事件与输入轮询；控制邮箱有界。
 * Reader、Operator、Sink 的业务逻辑放在各自子类，不能在这里硬编码 Source 特例。
 *
 * <p>InputStatus.NOTHING_AVAILABLE 时等待输入可用性 Future 或邮箱唤醒，
 * 不用固定 sleep；下游阻塞写入必须响应任务取消时的线程中断。
 *
 * <p>本阶段不提供分布式调度或全局 Checkpoint 语义。
 */
public abstract class StreamTask implements AutoCloseable {

    private static final int MAILBOX_CAPACITY = 128;
    private static final int MAX_COMMANDS_PER_TURN = 32;
    private static final int MAX_EMPTY_READY_FUTURES = 64;

    private final TaskEnvironment environment;
    private final ArrayBlockingQueue<Runnable> mailbox = new ArrayBlockingQueue<>(MAILBOX_CAPACITY);
    private final Object lifecycleLock = new Object();
    private final List<CompletableFuture<?>> replies = new ArrayList<>();
    private final AtomicReference<Throwable> asyncFailure = new AtomicReference<>();
    private final CompletableFuture<Void> started = new CompletableFuture<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final CompletableFuture<Void> cancellation = new CompletableFuture<>();
    private volatile Thread worker;
    private volatile boolean cancelRequested;
    private boolean startRequested;

    protected StreamTask(TaskEnvironment environment) {
        this.environment = Objects.requireNonNull(environment, "environment 不能为空")
                .withCancellation(() -> cancelRequested);
    }

    /** 当前 Task 的实际运行身份（不是默认配置）。 */
    public final RuntimeTaskInfo taskInfo() {
        return environment.taskInfo();
    }

    /** 仅向所属 Task 组件暴露只读运行上下文。 */
    protected final TaskEnvironment taskEnvironment() {
        return environment;
    }

    /** 初始化执行组件，在工作线程启动。 */
    protected abstract void openTask() throws Exception;

    /** 按照源头或下游输入类型处理一轮数据；不可把暂时缺数据误判为结束。 */
    protected abstract InputStatus processInput() throws Exception;

    /** 输入暂时不可用时，返回一份会在数据可用时完成的 Future。 */
    protected abstract CompletableFuture<Void> getAvailableFuture();

    /** 输入自然结束时串行完成 Operator.finish 与 Sink.flush(true)。取消或失败时不调用。 */
    protected void finishTask() throws Exception {}

    /** 释放当前 Task 所持有的所有运行资源。 */
    protected abstract void closeTask() throws Exception;

    /** 异常退出后的通知钩子。不得阻塞等待本 Task 的 completionFuture。 */
    protected void taskFailed(Throwable failure) {}

    /** 启动一次工作线程，Future 在 openTask() 成功后完成。 */
    public final CompletableFuture<Void> start() {
        synchronized (lifecycleLock) {
            if (startRequested || cancelRequested) {
                return CompletableFuture.failedFuture(new IllegalStateException("StreamTask 不允许重复启动或取消后启动"));
            }
            startRequested = true;
            Thread thread = Thread.ofVirtual().name(taskInfo().threadName()).unstarted(this::run);
            worker = thread;
            try {
                thread.start();
            } catch (Throwable failure) {
                worker = null;
                started.completeExceptionally(failure);
                completion.completeExceptionally(failure);
                cancellation.completeExceptionally(failure);
            }
        }
        return started.copy();
    }

    public final CompletableFuture<Void> completionFuture() {
        return completion.copy();
    }

    /** 发出取消请求，只有当前任务退出并释放资源后完成取消 Future。 */
    public final CompletableFuture<Void> cancelAsync() {
        Thread thread;
        synchronized (lifecycleLock) {
            if (cancelRequested) {
                return cancellation.copy();
            }
            if (completion.isDone()) {
                return CompletableFuture.failedFuture(new IllegalStateException("StreamTask 已结束"));
            }
            cancelRequested = true;
            thread = worker;
            if (thread == null) {
                CancellationException canceled = new CancellationException("任务启动前被取消");
                started.completeExceptionally(canceled);
                completion.completeExceptionally(canceled);
                failReplies(canceled);
                cancellation.complete(null);
            }
        }
        if (thread != null) {
            thread.interrupt();
            LockSupport.unpark(thread);
        }
        return cancellation.copy();
    }

    /** 异步将一次控制操作投递到 Mailbox；只有实际执行完成 Future 才成功。 */
    protected final <R> CompletableFuture<R> submitMailbox(Callable<R> action) {
        Objects.requireNonNull(action, "action 不能为空");
        CompletableFuture<R> reply = new CompletableFuture<>();
        synchronized (lifecycleLock) {
            if (!startRequested || cancelRequested || completion.isDone()) {
                reply.completeExceptionally(new IllegalStateException("StreamTask 尚未启动或已经结束"));
                return reply.copy();
            }
            Runnable command = () -> {
                try {
                    reply.complete(action.call());
                } catch (Throwable failure) {
                    reply.completeExceptionally(failure);
                    throw new CompletionException(failure);
                } finally {
                    synchronized (lifecycleLock) {
                        replies.remove(reply);
                    }
                }
            };
            if (!mailbox.offer(command)) {
                reply.completeExceptionally(new IllegalStateException("StreamTask 控制邮箱已满"));
                return reply.copy();
            }
            replies.add(reply);
        }
        wakeup();
        // 不能向外泄漏内部 Future；外部提前 complete() 会伪造事件已处理的确认。
        return reply.copy();
    }

    /** 从异步分片请求、IO 回调等非 Mailbox 线程通知 Task 失败。 */
    protected final void failAsync(Throwable failure) {
        if (asyncFailure.compareAndSet(null, Objects.requireNonNull(failure, "failure 不能为空"))) {
            wakeup();
        }
    }

    @Override
    public final void close() throws Exception {
        if (Thread.currentThread() == worker) {
            cancelAsync();
            return;
        }
        if (completion.isDone()) {
            return;
        }
        try {
            cancelAsync().get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        } catch (ExecutionException error) {
            throw new Exception("StreamTask 关闭失败", error.getCause());
        }
    }

    private void run() {
        Throwable failure = null;
        boolean finished = false;
        try {
            checkStop();
            openTask();
            checkStop();
            started.complete(null);
            int emptyReadyFutures = 0;
            while (true) {
                checkStop();
                for (int i = 0; i < MAX_COMMANDS_PER_TURN; i++) {
                    Runnable command = mailbox.poll();
                    if (command == null) {
                        break;
                    }
                    command.run();
                    checkStop();
                }
                InputStatus status = Objects.requireNonNull(processInput(), "processInput 不能返回 null");
                switch (status) {
                    case MORE_AVAILABLE -> emptyReadyFutures = 0;
                    case END_OF_INPUT -> {
                        checkStop();
                        finishTask();
                        checkStop();
                        finished = true;
                        return;
                    }
                    case NOTHING_AVAILABLE -> {
                        CompletableFuture<Void> available = Objects.requireNonNull(
                                getAvailableFuture(), "getAvailableFuture 不能返回 null");
                        if (available.isDone()) {
                            available.join();
                            if (++emptyReadyFutures >= MAX_EMPTY_READY_FUTURES) {
                                throw new IllegalStateException("SourceReader.isAvailable() 持续已完成，但没有数据可读");
                            }
                            continue;
                        }
                        emptyReadyFutures = 0;
                        available.whenComplete((unused, error) -> {
                            if (error != null) {
                                failAsync(error);
                            } else {
                                wakeup();
                            }
                        });
                        if (mailbox.isEmpty() && !available.isDone() && !cancelRequested
                                && asyncFailure.get() == null) {
                            LockSupport.park(this);
                        }
                    }
                }
            }
        } catch (Throwable error) {
            failure = error;
        } finally {
            try {
                closeTask();
            } catch (Throwable closeError) {
                if (failure == null) {
                    failure = closeError;
                } else if (failure != closeError) {
                    failure.addSuppressed(closeError);
                }
            }
            if (cancelRequested && (failure == null
                    || failure instanceof InterruptedException
                    || failure instanceof CancellationException)) {
                failure = new CancellationException("StreamTask 已取消");
            }
            if (failure == null && !finished) {
                failure = new IllegalStateException("StreamTask 未完成输入就退出");
            }
            if (failure == null && asyncFailure.get() != null) {
                failure = asyncFailure.get();
            }
            if (failure != null && !(failure instanceof CancellationException)) {
                try {
                    taskFailed(failure);
                } catch (Throwable callbackFailure) {
                    failure.addSuppressed(callbackFailure);
                }
            }
            if (!started.isDone()) {
                started.completeExceptionally(failure == null
                        ? new IllegalStateException("StreamTask 未启动") : failure);
            }
            failReplies(failure == null
                    ? new IllegalStateException("StreamTask 已结束") : failure);
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

    private void checkStop() {
        if (cancelRequested) {
            throw new CancellationException("StreamTask 已取消");
        }
        Throwable failure = asyncFailure.get();
        if (failure != null) {
            throw new CompletionException(failure);
        }
    }

    private void wakeup() {
        Thread thread = worker;
        if (thread != null) {
            LockSupport.unpark(thread);
        }
    }

    private void failReplies(Throwable cause) {
        synchronized (lifecycleLock) {
            for (CompletableFuture<?> reply : replies) {
                reply.completeExceptionally(cause);
            }
            replies.clear();
            mailbox.clear();
        }
    }
}
