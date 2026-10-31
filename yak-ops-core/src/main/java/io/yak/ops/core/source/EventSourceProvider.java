package io.yak.ops.core.source;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.event.Event;

/** Provides the reusable YakFlow Source that emits a connector's event stream. */
@FunctionalInterface
public interface EventSourceProvider {
    Source<Event, ?, ?> getSource();
}
