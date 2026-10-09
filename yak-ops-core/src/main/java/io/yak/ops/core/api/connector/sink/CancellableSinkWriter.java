package io.yak.ops.core.api.connector.sink;

/**
 * Optional, non-blocking cancellation hook for a Sink Writer with potentially blocking I/O.
 *
 * <p>The runtime may invoke this on a cancellation thread concurrently with write or flush.
 * Implementations must be idempotent, thread-safe, and return without waiting for JDBC/network
 * operations to complete. This hook may signal or abort active I/O but must not flush, commit,
 * or close resources; close remains owned by the task mailbox.
 *
 * @param <T> the Sink Writer's input type
 */
public interface CancellableSinkWriter<T> extends SinkWriter<T> {

    /** Requests terminal cancellation without waiting for the in-flight write or flush. */
    void cancel();
}
