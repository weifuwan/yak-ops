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
    private final TaskProcessingTimeService processingTimeService;

    private volatile Thread worker;
    private volatile boolean cancelRequested;
    private boolean startRequested;
    private volatile boolean dataFinished;
    private boolean inputFinished;
    private boolean finished;
    private boolean readyWithoutInput;
    private final CompletableFuture<Void> endOfDataReached = new CompletableFuture<>();
    private CompletableFuture<Void> dataAcknowledged = CompletableFuture.completedFuture(null);
    private CompletableFuture<Void> finalCheckpointCompleted = CompletableFuture.completedFuture(null);

    protected StreamTask(TaskEnvironment environment) {
        TaskEnvironment original = Objects.requireNonNull(environment, "environment must not be null");
        mailboxProcessor = new MailboxProcessor(
                new TaskMailboxImpl(),
                this::processDefaultAction,
                this::checkStop,
                () -> cancelRequested || asyncFailure.get() != null);
        mailboxExecutor = mailboxProcessor.getMailboxExecutor();
        processingTimeService = new TaskProcessingTimeService(
                mailboxExecutor, this::failAsync, () -> cancelRequested || completion.isDone());
        this.environment =
                original.withCancellation(() -> cancelRequested).withProcessingTimeService(processingTimeService);
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

    /** Finishes business logic once, without closing the physical output channel. */
    protected void finishTask() throws Exception {}

    /** Returns downstream consumption acknowledgement after the task's final records. */
    protected CompletableFuture<Void> endOfDataAcknowledgement() {
        return CompletableFuture.completedFuture(null);
    }

    /** Closes the physical output channel after downstream data acknowledgement. */
    protected void finishOutput() throws Exception {}

    /** True once the normal business finish succeeded; checkpoint coordination may still be active. */
    public final boolean hasReachedEndOfData() {
        return dataFinished;
    }

    /** Invoked before starting a bounded Task whose physical exit requires a terminal checkpoint. */
    public final void requireFinalCheckpoint(CompletableFuture<Void> completed) {
        synchronized (lifecycleLock) {
            if (startRequested) {
                throw new IllegalStateException("Final checkpoint fence must be set before task start");
            }
            finalCheckpointCompleted = Objects.requireNonNull(completed, "completed");
        }
    }

    /** Signals successful logical operator completion, independently of physical task termination. */
    public final CompletableFuture<Void> endOfDataFuture() {
        return endOfDataReached.copy();
    }

    /** Includes downstream acknowledgment of every final record emitted by this Task. */
    public final CompletableFuture<Void> allDataProcessedFuture() {
        return endOfDataReached.thenCompose(unused -> dataAcknowledged).copy();
    }

    /** Completes business input on the mailbox thread, without shutting down control processing. */
    protected final void endData() throws Exception {
        if (dataFinished) {
            throw new IllegalStateException("Task has already ended data");
        }
        checkStop();
        finishTask();
        checkStop();
        dataAcknowledged = Objects.requireNonNull(endOfDataAcknowledgement(), "Missing EndOfData acknowledgment");
        dataFinished = true;
        endOfDataReached.complete(null);
    }

    /** Close every resource acquired by openTask or input processing. */
    protected abstract void closeTask() throws Exception;

    protected void taskFailed(Throwable failure) {}

    /** Starts exactly one mailbox thread; returns after openTask succeeded. */
    public final CompletableFuture<Void> start() {
        synchronized (lifecycleLock) {
            if (startRequested || cancelRequested) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("StreamTask cannot start more than once or after cancellation"));
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

    /**
     * Returns the completion signal for this task attempt.
     *
     * <p>The future completes normally after a successful task shutdown or exceptionally
     * after execution, cancellation or cleanup failure.
     *
     * @return a detached view of the task's terminal completion
     */
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
                return CompletableFuture.failedFuture(new IllegalStateException("StreamTask has terminated"));
            }
            cancelRequested = true;
            thread = worker;
            if (thread == null) {
                CancellationException canceled = new CancellationException("Task was cancelled before starting");
                started.completeExceptionally(canceled);
                completion.completeExceptionally(canceled);
                mailboxProcessor.close(canceled);
                cancellation.complete(null);
            }
        }
        processingTimeService.close();
        if (thread != null) {
            try {
                onCancellationRequested();
            } catch (Throwable cancelFailure) {
                failAsync(cancelFailure);
            } finally {
                thread.interrupt();
            }
        }
        return cancellation.copy();
    }

    /**
     * Signals active I/O from the cancellation caller thread before interrupting the Task.
     *
     * <p>This hook must be non-blocking, idempotent, and must not flush or close the Writer.
     * Terminal resource release still runs on the owning Task mailbox.
     */
    protected void onCancellationRequested() {}

    /** Control mail is acknowledged after it actually executes on the Task thread. */
    protected final <R> CompletableFuture<R> submitMailbox(Callable<R> action) {
        Objects.requireNonNull(action, "action must not be null");
        synchronized (lifecycleLock) {
            if (!startRequested || cancelRequested || completion.isDone()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("StreamTask has not started or has already terminated"));
            }
            return mailboxExecutor.submit(action);
        }
    }

    /** Explicitly recheck input after a control mail changes its availability state. */
    protected final void resumeInputProcessing() {
        mailboxProcessor.resumeDefaultAction();
    }

    /** Propagate asynchronous Coordinator / Reader availability failure to the owning mailbox. */
    protected final void failAsync(Throwable failure) {
        if (completion.isDone()) {
            return;
        }
        if (asyncFailure.compareAndSet(null, Objects.requireNonNull(failure, "failure must not be null"))) {
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
            throw new Exception("Failed to close StreamTask", failure.getCause());
        }
    }

    /** Input processing is the mailbox's default action, not a separate polling loop. */
    private void processDefaultAction(MailboxDefaultAction.Controller controller) throws Exception {
        checkStop();
        if (inputFinished) {
            finishPhysicalTask(controller);
            return;
        }
        InputStatus status = Objects.requireNonNull(processInput(), "processInput must not return null");
        switch (status) {
            case MORE_AVAILABLE -> readyWithoutInput = false;
            case END_OF_INPUT -> {
                if (!dataFinished) {
                    endData();
                }
                inputFinished = true;
                finishPhysicalTask(controller);
            }
            case NOTHING_AVAILABLE -> {
                CompletableFuture<Void> availability =
                        Objects.requireNonNull(getAvailableFuture(), "getAvailableFuture must not return null");
                if (availability.isDone()) {
                    // A wakeup can race with pollNext(). Retry once, but reject a permanently
                    // completed readiness future instead of spinning with arbitrary retry limits.
                    availability.join();
                    if (readyWithoutInput) {
                        throw new IllegalStateException(
                                "SourceReader.isAvailable() remains completed without readable input");
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

    private void finishPhysicalTask(MailboxDefaultAction.Controller controller) throws Exception {
        CompletableFuture<Void> ready = CompletableFuture.allOf(dataAcknowledged, finalCheckpointCompleted);
        if (!ready.isDone()) {
            MailboxDefaultAction.Suspension suspension = controller.suspendDefaultAction();
            ready.whenComplete((unused, error) -> {
                if (error != null) {
                    failAsync(error);
                }
                suspension.resume();
            });
            return;
        }
        ready.join();
        checkStop();
        finishOutput();
        checkStop();
        finished = true;
        controller.allActionsCompleted();
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
            processingTimeService.close();
            try {
                closeTask();
            } catch (Throwable closeError) {
                if (failure == null) {
                    failure = closeError;
                } else if (failure != closeError) {
                    failure.addSuppressed(closeError);
                }
            }
            if (cancelRequested
                    && (failure == null
                            || failure instanceof InterruptedException
                            || failure instanceof CancellationException)) {
                failure = new CancellationException("StreamTask was cancelled");
            }
            if (failure == null && !finished) {
                failure = new IllegalStateException("StreamTask exited before finishing its input");
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
                started.completeExceptionally(
                        failure == null ? new IllegalStateException("StreamTask was not started") : failure);
            }
            mailboxProcessor.close(failure == null ? new IllegalStateException("StreamTask has terminated") : failure);
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
            throw new CancellationException("StreamTask was cancelled");
        }
        Throwable failure = asyncFailure.get();
        if (failure != null) {
            throw new CompletionException(failure);
        }
    }
}
