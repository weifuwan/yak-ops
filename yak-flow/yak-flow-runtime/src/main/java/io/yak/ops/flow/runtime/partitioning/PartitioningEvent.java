package io.yak.ops.flow.runtime.partitioning;

import io.yak.ops.core.event.Event;
import java.util.Objects;

/** An event with the target subtask selected by the pre-partition operator. */
public final class PartitioningEvent implements Event {

    private final Event payload;
    private final int targetPartition;

    private PartitioningEvent(Event payload, int targetPartition) {
        this.payload = Objects.requireNonNull(payload, "payload");
        if (targetPartition < 0) {
            throw new IllegalArgumentException("Target partition must be nonnegative");
        }
        this.targetPartition = targetPartition;
    }

    /** Creates a regular-topology event without a source partition identifier. */
    public static PartitioningEvent ofRegular(Event payload, int targetPartition) {
        return new PartitioningEvent(payload, targetPartition);
    }

    public Event getPayload() {
        return payload;
    }

    public int getTargetPartition() {
        return targetPartition;
    }

    @Override
    public String toString() {
        return "PartitioningEvent{targetPartition=" + targetPartition + ", payload=redacted}";
    }
}
