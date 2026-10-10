package io.yak.ops.connector.base.sink.writer;

/**
 * Defines the only owner of pending Sink records and destination-specific batch I/O.
 *
 * <p>Implementations detach mutable inputs before buffering them. The shared Writer owns no
 * parallel row queue; it invokes this output only on the task mailbox except for the terminal
 * nonblocking cancellation signal, which may arrive concurrently.
 *
 * <p>Committing, retrying or flushing from {@link #close()} or {@link #cancel()} is forbidden.
 *
 * @param <T> input record type
 */
public interface BatchOutput<T> extends AutoCloseable {

    /**
     * Validates and detaches one incoming record into the output's sole pending buffer.
     *
     * @param record input that may be reused by its upstream operator
     * @throws Exception if validation, copying or staging fails
     */
    void add(T record) throws Exception;

    /**
     * Counts staged records that have not yet been successfully committed.
     *
     * @return pending records visible to the shared batch-size trigger
     */
    int bufferedRecords();

    /**
     * Whether automatic size and processing-time triggers may flush this output.
     *
     * <p>An output containing the first half of an indivisible update can temporarily
     * defer automatic flush without splitting the pair. Explicit checkpoint and end-of-input
     * flushes still invoke {@link #flush()} and must reject incomplete input, not
     * acknowledge it.
     *
     * @return false only while an automatic flush would split an indivisible logical event
     */
    default boolean canAutomaticallyFlush() {
        return true;
    }

    /**
     * Flushes pending records at a batch boundary, checkpoint, or normal end of input.
     *
     * <p>Outputs that commit externally must acknowledge records only after confirmed
     * success. An incomplete logical update or failed transaction must not be discarded.
     *
     * @throws Exception if writing, committing, or validating the pending batch fails
     */
    void flush() throws Exception;

    /**
     * Requests cancellation of active I/O without blocking or committing.
     *
     * <p>May be invoked concurrently with flush. Resource cleanup remains in close().
     */
    default void cancel() {}

    /** Releases output resources without implicitly flushing or committing pending records. */
    @Override
    void close() throws Exception;
}
