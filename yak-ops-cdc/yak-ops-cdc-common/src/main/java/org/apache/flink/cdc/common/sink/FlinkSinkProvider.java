package org.apache.flink.cdc.common.sink;

import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.event.Event;

/**
 * {@code FlinkSinkProvider} is used to provide a Flink {@link Sink} for writing events to external
 * systems.
 */
@PublicEvolving
public interface FlinkSinkProvider extends EventSinkProvider {

    /** Get the {@link Sink} for writing events to external systems. */
    Sink<Event> getSink();

    /** Create a {@link FlinkSinkProvider} from a {@link Sink}. */
    static FlinkSinkProvider of(Sink<Event> sink) {
        return () -> sink;
    }
}
