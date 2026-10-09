package io.yak.ops.core.api.connector.sink;

/**
 * Processing-time timers for a Sink Writer, with callbacks serialized on its owning task mailbox.
 *
 * <p>The runtime owns scheduling threads and timer cleanup. A timer callback must never execute
 * concurrently with the Writer's write, flush, snapshot, or close lifecycle methods.
 */
public interface ProcessingTimeService {

    /** Returns current wall-clock time in milliseconds since the Unix epoch. */
    long currentProcessingTime();

    /**
     * Schedules a callback to execute on the owning task mailbox at or after the timestamp.
     *
     * <p>Cancellation prevents a callback that has not started; it does not interrupt one that
     * is already executing. Callback failure must fail the owning task.
     */
    TimerHandle registerTimer(long timestampMillis, Runnable callback);

    /** Cancellation handle for an outstanding processing-time timer. */
    @FunctionalInterface
    interface TimerHandle {
        boolean cancel();
    }
}
