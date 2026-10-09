package io.yak.ops.flow.runtime.operators;

/**
 * Minimal Flink-style operator lifecycle. Calls are serialized on the owning StreamTask mailbox.
 * The current Runtime intentionally has no operator state or barrier snapshot callback yet.
 */
public interface StreamOperator extends AutoCloseable {

    default void open() throws Exception {}

    /** Successful end-of-input; never invoked for failure or forced cancellation. */
    default void finish() throws Exception {}

    @Override
    default void close() throws Exception {}
}
