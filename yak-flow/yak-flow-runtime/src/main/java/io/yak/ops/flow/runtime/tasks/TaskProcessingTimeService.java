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
 * Task-owned processing-time service that dispatches callbacks onto the task mailbox.
 *
 * <p>The lazily created scheduler only measures time; it never calls a Writer or modifies
 * checkpoint state. A failed callback fails the owning task, and cancellation or task shutdown
 * prevents late timers from mutating a closed writer.
 */
public final class TaskProcessingTimeService implements ProcessingTimeService, AutoCloseable {

    private final MailboxExecutor mailbox;
    private final Consumer<Throwable> failTask;
    private final BooleanSupplier stopping;
    private final Object lock = new Object();
    private ScheduledExecutorService scheduler;
    private volatile boolean closed;

    public TaskProcessingTimeService(
            MailboxExecutor mailbox, Consumer<Throwable> failTask, BooleanSupplier stopping) {
        this.mailbox = Objects.requireNonNull(mailbox, "mailbox");
        this.failTask = Objects.requireNonNull(failTask, "failTask");
        this.stopping = Objects.requireNonNull(stopping, "stopping");
    }

    @Override
    public long currentProcessingTime() {
        return System.currentTimeMillis();
    }

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
