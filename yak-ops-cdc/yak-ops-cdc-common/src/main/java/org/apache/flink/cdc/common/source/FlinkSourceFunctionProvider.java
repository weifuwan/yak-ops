package org.apache.flink.cdc.common.source;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

/**
 * {@code FlinkSourceFunctionProvider} is used to provide a Flink {@link SourceFunction} for reading
 * events from external systems.
 */
@PublicEvolving
public interface FlinkSourceFunctionProvider extends EventSourceProvider {

    /** Get the {@link SourceFunction} for reading events from external systems. */
    SourceFunction<Event> getSourceFunction();

    /** Create a {@link FlinkSourceFunctionProvider} from a {@link SourceFunction}. */
    static FlinkSourceFunctionProvider of(SourceFunction<Event> sourceFunction) {
        return () -> sourceFunction;
    }
}
