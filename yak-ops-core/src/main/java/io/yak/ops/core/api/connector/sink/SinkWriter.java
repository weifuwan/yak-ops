package io.yak.ops.core.api.connector.sink;

/**
* Per-subtask instance that writes records and flushes its buffered output.
*
* <p>The runtime serializes calls to write, flush and close on the owning task thread.
* A successful flush does not imply a transactional commit or exactly-once delivery.
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
    * a normal end of input. Success is not proof of an end-to-end transactional commit.
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
