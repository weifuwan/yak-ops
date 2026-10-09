package io.yak.ops.flow.runtime.operators;

/**
 * Common lifecycle of an operator confined to its owning StreamTask mailbox thread.
 *
 * <p>Operators with durable state implement {@link CheckpointedStreamOperator}; the basic
 * lifecycle does not itself own snapshots or checkpoint coordination.
 */
public interface StreamOperator extends AutoCloseable {

    default void open() throws Exception {}

    /** Successful end-of-input; never invoked for failure or forced cancellation. */
    default void finish() throws Exception {}

    @Override
    default void close() throws Exception {}
}
