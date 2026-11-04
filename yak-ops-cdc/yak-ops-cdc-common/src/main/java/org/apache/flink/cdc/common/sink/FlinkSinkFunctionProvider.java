package org.apache.flink.cdc.common.sink;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

/**
 * {@code FlinkSinkFunctionProvider} is used to provide a Flink {@link SinkFunction} for writing
 * events to external systems.
 */
@PublicEvolving
public interface FlinkSinkFunctionProvider extends EventSinkProvider {

    /** Get the {@link SinkFunction} for writing events to external systems. */
    SinkFunction<Event> getSinkFunction();

    /** Create a {@link FlinkSinkFunctionProvider} from a {@link SinkFunction}. */
    static FlinkSinkFunctionProvider of(SinkFunction<Event> sinkFunction) {
        return () -> sinkFunction;
    }
}
