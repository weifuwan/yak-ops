package io.yak.ops.flow.runtime.operators;

import io.yak.ops.flow.runtime.state.OperatorStateBackend;

/**
 * Explicit mailbox-thread state lifecycle for an operator with durable local state.
 *
 * <p>initializeState runs before open; snapshotState runs after all input records before the
 * aligned barrier and before the barrier is forwarded. Implementations persist state through
 * the supplied backend rather than holding references to checkpoint byte arrays.
 */
public interface CheckpointedStreamOperator extends StreamOperator {

    void initializeState(OperatorStateBackend backend) throws Exception;

    void snapshotState(long checkpointId, OperatorStateBackend backend) throws Exception;
}
