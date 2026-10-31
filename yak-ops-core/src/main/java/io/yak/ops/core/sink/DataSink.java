package io.yak.ops.core.sink;

import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.function.HashFunctionProvider;

/** An event sink, its metadata applier and its deterministic partitioning contract. */
public interface DataSink {
    EventSinkProvider getEventSinkProvider();

    MetadataApplier getMetadataApplier();

    default HashFunctionProvider<DataChangeEvent> getDataChangeEventHashFunctionProvider() {
        return new DefaultDataChangeEventHashFunctionProvider();
    }

    default HashFunctionProvider<DataChangeEvent> getDataChangeEventHashFunctionProvider(int parallelism) {
        return getDataChangeEventHashFunctionProvider();
    }
}
