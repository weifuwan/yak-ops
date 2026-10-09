package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.tasks.mailbox.MailboxDefaultAction;
import io.yak.ops.flow.runtime.tasks.mailbox.MailboxExecutor;
import io.yak.ops.flow.runtime.tasks.mailbox.MailboxProcessor;
import io.yak.ops.flow.runtime.tasks.mailbox.TaskMailboxImpl;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * StreamTask owns one execution thread and an independent MailboxProcessor.
 *
 * <p>Only the mailbox thread invokes Reader / Operator / Writer lifecycle and input methods.
 * Control events run as mails; processing input is the suspendable default action. No second
 * control thread, fixed polling interval or arbitrary mail-draining budget is involved.
 */
public abstract class StreamTask implements AutoCloseable {

    private final TaskEnvironment environment;
    private final Object lifecycleLock = new Object();
    private final AtomicReference<Throwable> asyncFailure = new AtomicReference<>();
    private final CompletableFuture<Void> started = new CompletableFuture<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final CompletableFuture<Void> cancellation = new CompletableFuture<>();
    private final MailboxProcessor mailboxProcessor;
    private final MailboxExecutor mailboxExecutor;

    private volatile Thread worker;
    private volatile boolean cancelRequested;
    private boolean startRequested;
    private boolean finished;
    private boolean readyWithoutInput;

    protected StreamTask(TaskEnvironment environment) {
        this.environment = Objects.requireNonNull(environment, "environment 不能为空")
                .withCancellation(() -> cancelRequested);
        mailboxProcessor = new MailboxProcessor(
                new TaskMailboxImpl(),
                this::processDefaultAction,
                this::checkStop,
                () -> cancelRequested || asyncFailure.get() != null);
        mailboxExecutor = mailboxProcessor.getMailboxExecutor();
    }

    public final RuntimeTaskInfo taskInfo() {
        return environment.taskInfo();
    }

    protected final TaskEnvironment taskEnvironment() {
        return environment;
    }

    /** Initialize Reader / Operator / SinkWriter on the owning Task thread. */
    protected abstract void openTask() throws Exception;

    /** Process one non-blocking input step. NOTHING_AVAILABLE must provide an availability future. */
    protected abstract InputStatus processInput() throws Exception;

    protected abstract CompletableFuture<Void> getAvailableFuture();

    /** Invoked exclusively on normal END_OF_INPUT, never after failure or cancellation. */
    protected void finishTask() throws Exception {}

    /** Close every resource acquired by openTask or input processing. */
    protected abstract void closeTask() throws Exception;

    protected void taskFailed(Throwable failure) {}

    /** Starts exactly one mailbox thread; returns after openTask succeeded. */
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
                mailboxProcessor.close(failure);
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

    /** Cancellation completes only once the Task thread has exited and cleaned up its resources. */
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
                mailboxProcessor.close(canceled);
                cancellation.complete(null);
            }
        }
        if (thread != null) {
            thread.interrupt();
        }
        return cancellation.copy();
    }

    /** Control mail is acknowledged after it actually executes on the Task thread. */
    protected final <R> CompletableFuture<R> submitMailbox(Callable<R> action) {
        Objects.requireNonNull(action, "action 不能为空");
        synchronized (lifecycleLock) {
            if (!startRequested || cancelRequested || completion.isDone()) {
                return CompletableFuture.failedFuture(new IllegalStateException("StreamTask 尚未启动或已经结束"));
            }
            return mailboxExecutor.submit(action);
        }
    }

    /** Propagate asynchronous Coordinator / Reader availability failure to the owning mailbox. */
    protected final void failAsync(Throwable failure) {
        if (completion.isDone()) {
            return;
        }
        if (asyncFailure.compareAndSet(null, Objects.requireNonNull(failure, "failure 不能为空"))) {
            mailboxProcessor.wakeup();
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
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw failure;
        } catch (ExecutionException failure) {
            throw new Exception("StreamTask 关闭失败", failure.getCause());
        }
    }

    /** Input processing is the mailbox's default action, not a separate polling loop. */
    private void processDefaultAction(MailboxDefaultAction.Controller controller) throws Exception {
        checkStop();
        InputStatus status = Objects.requireNonNull(processInput(), "processInput 不能返回 null");
        switch (status) {
            case MORE_AVAILABLE -> readyWithoutInput = false;
            case END_OF_INPUT -> {
                checkStop();
                finishTask();
                checkStop();
                finished = true;
                controller.allActionsCompleted();
            }
            case NOTHING_AVAILABLE -> {
                CompletableFuture<Void> availability = Objects.requireNonNull(
                        getAvailableFuture(), "getAvailableFuture 不能返回 null");
                if (availability.isDone()) {
                    // A wakeup can race with pollNext(). Retry once, but reject a permanently
                    // completed readiness future instead of spinning with arbitrary retry limits.
                    availability.join();
                    if (readyWithoutInput) {
                        throw new IllegalStateException("SourceReader.isAvailable() 持续已完成，但没有数据可读");
                    }
                    readyWithoutInput = true;
                    return;
                }
                readyWithoutInput = false;
                MailboxDefaultAction.Suspension suspension = controller.suspendDefaultAction();
                availability.whenComplete((unused, error) -> {
                    if (error != null) {
                        failAsync(error);
                    }
                    suspension.resume();
                });
            }
        }
    }

    private void run() {
        Throwable failure = null;
        try {
            checkStop();
            openTask();
            checkStop();
            started.complete(null);
            mailboxProcessor.runMailboxLoop();
            checkStop();
        } catch (Throwable error) {
            failure = error;
        } finally {
            mailboxProcessor.prepareClose();
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
            mailboxProcessor.close(failure == null
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
}
