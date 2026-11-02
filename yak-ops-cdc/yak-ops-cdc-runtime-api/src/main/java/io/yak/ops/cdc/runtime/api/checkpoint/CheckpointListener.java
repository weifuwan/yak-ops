package io.yak.ops.cdc.runtime.api.checkpoint;

/** Flink 1.20.3 compatible checkpoint completion and abortion notifications. */
public interface CheckpointListener {
    void notifyCheckpointComplete(long checkpointId) throws Exception;

    default void notifyCheckpointAborted(long checkpointId) throws Exception {}
}
