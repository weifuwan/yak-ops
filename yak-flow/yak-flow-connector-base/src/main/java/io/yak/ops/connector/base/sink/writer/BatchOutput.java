package io.yak.ops.connector.base.sink.writer;

/**
 * The single owner of buffered Sink records and their destination-specific output.
 *
 * <p>Implementations must retain a detached copy of records if the caller can reuse or mutate
 * their backing storage. The base Writer does not allocate a second record queue.
 *
 * @param <T> input record type
 */
public interface BatchOutput<T> extends AutoCloseable {

    /** Accepts one record into this output's bounded buffer. */
    void add(T record) throws Exception;

    /** Number of records currently awaiting a successful flush. */
    int bufferedRecords();

    /**
     * Whether automatic size and processing-time triggers may flush this output.
     *
     * <p>An output containing the first half of an indivisible update can temporarily
     * defer automatic flush without splitting the pair. Explicit checkpoint and end-of-input
     * flushes still invoke flush() and must reject incomplete input, not acknowledge it.
     */
    default boolean canAutomaticallyFlush() {
        return true;
    }

    /** Flushes pending records; incomplete logical updates and failed writes must be rejected. */
    void flush() throws Exception;

    /**
     * Requests cancellation of active I/O without blocking or committing.
     *
     * <p>May be invoked concurrently with flush. Resource cleanup remains in close().
     */
    default void cancel() {}

    /** Releases resources without implicitly flushing pending records. */
    @Override
    void close() throws Exception;
}
