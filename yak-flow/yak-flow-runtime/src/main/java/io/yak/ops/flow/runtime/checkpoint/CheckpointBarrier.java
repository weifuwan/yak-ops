package io.yak.ops.flow.runtime.checkpoint;

/** Ordered, non-record control event on each producer-to-consumer ResultSubpartition. */
public record CheckpointBarrier(long checkpointId) {
    public CheckpointBarrier {
        if (checkpointId <= 0) {
            throw new IllegalArgumentException("Checkpoint ID must be positive");
        }
    }
}
