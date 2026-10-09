package io.yak.ops.flow.runtime.checkpoint;

/**
 * A checkpoint could not establish a complete barrier cut (for example an input reached EOF).
 * The running job may still finish successfully; no checkpoint state is published.
 */
public final class CheckpointDeclinedException extends Exception {

    public CheckpointDeclinedException(long checkpointId, String reason) {
        super("Checkpoint " + checkpointId + " declined: " + reason);
    }
}
