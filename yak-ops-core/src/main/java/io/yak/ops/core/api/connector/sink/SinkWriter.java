package io.yak.ops.core.api.connector.sink;

/**
 * Defines one Sink subtask's record-writing, checkpoint-flush, and resource lifecycle.
 *
 * <p>The Runtime serializes write, flush and close on the task mailbox. A concrete Connector
 * may own a database transaction, but the Core interface does not imply a commit protocol
 * or exactly-once guarantee. Cancellation is an optional separate contract.
 *
 * @param <T> the input record type
 * @author weifuwan
 */
public interface SinkWriter<T> extends AutoCloseable {

    /**
     * Metadata for the current input record.
     *
     * <p>Until event time and watermarks are implemented, the runtime supplies a null timestamp
     * and {@link Long#MIN_VALUE} as the watermark.
     */
    interface Context {
        /** Returns the record timestamp, or null when none is available. */
        Long timestamp();

        /** Returns the current event-time watermark, or {@link Long#MIN_VALUE} when absent. */
        long currentWatermark();
    }

    /**
     * Writes or buffers a record for the current subtask.
     *
     * @param element the record to write
     * @param context metadata for this record
     * @throws Exception if writing fails
     */
    void write(T element, Context context) throws Exception;

    /**
     * Flushes buffered records during a checkpoint or at the normal end of input.
     *
     * <p>The runtime passes {@code false} at an aligned checkpoint and {@code true} only for
     * a normal end of input. A Connector must fail rather than acknowledge an incomplete
     * logical mutation. Success is not proof of an end-to-end transactional commit.
     *
     * @param endOfInput whether input has ended normally
     * @throws Exception if flushing fails
     */
    void flush(boolean endOfInput) throws Exception;

    /**
     * Releases resources after normal completion, cancellation or failure.
     *
     * <p>Closing does not imply a successful final flush; the runtime explicitly calls
     * {@code flush(true)} before closing a normally finished Writer.
     *
     * @throws Exception if resource cleanup fails
     */
    @Override
    void close() throws Exception;
}
