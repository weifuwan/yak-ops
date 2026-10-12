package io.yak.ops.flow.runtime.source.coordinator;

/** Signals that checkpoint-related split deliveries are awaiting reader acknowledgements. */
public final class PendingCheckpointDeliveriesException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public PendingCheckpointDeliveriesException() {
        super("Source coordinator has pending split or end-of-input deliveries");
    }
}
