package io.yak.ops.flow.runtime.source.event;

import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;
import java.util.Objects;

/** Connector-specific event delivered through the existing attempt-aware OperatorEvent gateways. */
public record SourceEventWrapper(SourceEvent sourceEvent) implements OperatorEvent {

    public SourceEventWrapper {
        Objects.requireNonNull(sourceEvent, "sourceEvent");
    }
}
