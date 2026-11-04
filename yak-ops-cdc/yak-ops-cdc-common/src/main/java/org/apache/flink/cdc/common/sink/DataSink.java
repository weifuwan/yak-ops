package org.apache.flink.cdc.common.sink;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.event.DataChangeEvent;
import org.apache.flink.cdc.common.function.HashFunctionProvider;

/**
 * {@code DataSink} is used to write change data to external system and apply metadata changes to
 * external systems as well.
 */
@PublicEvolving
public interface DataSink {

    /** Get the {@link EventSinkProvider} for writing changed data to external systems. */
    EventSinkProvider getEventSinkProvider();

    /** Get the {@link MetadataApplier} for applying metadata changes to external systems. */
    MetadataApplier getMetadataApplier();

    /**
     * Get the {@code HashFunctionProvider<DataChangeEvent>} for calculating hash value if you need
     * to partition by data change event before Sink.
     */
    default HashFunctionProvider<DataChangeEvent> getDataChangeEventHashFunctionProvider() {
        return new DefaultDataChangeEventHashFunctionProvider();
    }

    default HashFunctionProvider<DataChangeEvent> getDataChangeEventHashFunctionProvider(
            int parallelism) {
        return getDataChangeEventHashFunctionProvider(); // fallback to nullary version if it isn't
        // overridden
    }
}
