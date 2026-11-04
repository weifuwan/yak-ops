package org.apache.flink.cdc.common.source;

import org.apache.flink.api.connector.source.Source;
import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.event.Event;

/**
 * {@code FlinkSourceProvider} is used to provide a Flink {@link Source} for reading events from
 * external systems.
 */
@PublicEvolving
public interface FlinkSourceProvider extends EventSourceProvider {

    /** Get the {@link Source} for reading events from external systems. */
    Source<Event, ?, ?> getSource();

    /** Create a {@link FlinkSourceProvider} from a {@link Source}. */
    static FlinkSourceProvider of(Source<Event, ?, ?> source) {
        return () -> source;
    }
}
