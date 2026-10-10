package io.yak.ops.flow.runtime.tasks;

import io.yak.ops.core.api.connector.sink.ProcessingTimeService;
import io.yak.ops.flow.runtime.tasks.mailbox.MailboxExecutor;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Schedules processing-time callbacks without executing Connector code off the task mailbox.
 *
 * <p>A lazily created timer thread only measures time and submits mailbox messages.
 * Callback failure fails the owning task, while cancellation and task shutdown prevent
 * stale callbacks from accessing closed Source/Sink resources. The service does not own
 * Writer flush decisions or mutate checkpoint state.
 */
public final class TaskProcessingTimeService implements ProcessingTimeService, AutoCloseable {

    private final MailboxExecutor mailbox;
    private final Consumer<Throwable> failTask;
    private final BooleanSupplier stopping;
    private final Object lock = new Object();
    private ScheduledExecutorService scheduler;
    private volatile boolean closed;

    public TaskProcessingTimeService(MailboxExecutor mailbox, Consumer<Throwable> failTask, BooleanSupplier stopping) {
        this.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        this.failTask = Objects.requireNonNull(failTask, "failTask");
        this.stopping = Objects.requireNonNull(stopping, "stopping");
    }

    @Override
    public long currentProcessingTime() {
        return System.currentTimeMillis();
    }

    /**
     * Schedules a callback for mailbox execution at or after the requested wall-clock time.
     *
     * <p>The scheduling thread never invokes the callback directly. A cancelled timer
     * prevents an unstarted callback, and failed mailbox callbacks propagate to failTask.
     *
     * @param timestampMillis wall-clock trigger time in Unix epoch milliseconds
     * @param callback work to run on the owning task mailbox
     * @return a handle for canceling a callback before it runs
     * @throws IllegalStateException if the task is stopping or the service is closed
     */
    @Override
    public TimerHandle registerTimer(long timestampMillis, Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        synchronized (lock) {
            if (closed || stopping.getAsBoolean()) {
                throw new IllegalStateException("Task processing-time service is stopped");
            }
            if (scheduler == null) {
                scheduler = Executors.newSingleThreadScheduledExecutor(
                        Thread.ofVirtual().name("yak-task-processing-time-", 0).factory());
            }
            long now = currentProcessingTime();
            long delay = timestampMillis <= now ? 0L : timestampMillis - now;
            ScheduledFuture<?> scheduled = scheduler.schedule(() -> dispatch(callback), delay, TimeUnit.MILLISECONDS);
            return () -> scheduled.cancel(false);
        }
    }

    private void dispatch(Runnable callback) {
        if (closed || stopping.getAsBoolean()) {
            return;
        }
        mailbox.submit(() -> {
                    if (!closed && !stopping.getAsBoolean()) {
                        callback.run();
                    }
                    return null;
                })
                .whenComplete((ignored, failure) -> {
                    if (failure != null && !closed && !stopping.getAsBoolean()) {
                        Throwable cause = failure instanceof CompletionException wrapped ? wrapped.getCause() : failure;
                        failTask.accept(cause);
                    }
                });
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }
        }
    }
}
