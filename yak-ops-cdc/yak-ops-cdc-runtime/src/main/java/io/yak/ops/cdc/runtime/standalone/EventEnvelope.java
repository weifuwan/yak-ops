package io.yak.ops.cdc.runtime.standalone;

import java.util.Objects;
import org.apache.flink.cdc.common.event.Event;

/** Immutable data item transported through the bounded local channel. */
final class EventEnvelope {
    final Event event;
    final Long timestamp;

    EventEnvelope(Event event, Long timestamp) {
        this.event = Objects.requireNonNull(event, "event");
        this.timestamp = timestamp;
    }
}
