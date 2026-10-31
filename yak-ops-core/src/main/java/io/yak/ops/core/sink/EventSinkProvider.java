package io.yak.ops.core.sink;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.event.Event;

/** Provides the reusable YakFlow Sink that consumes a connector's event stream. */
@FunctionalInterface
public interface EventSinkProvider {
    Sink<Event> getSink();
}
