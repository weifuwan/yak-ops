package io.yak.ops.core.sink;

import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.function.HashFunctionProvider;

/** An event sink, its metadata applier and its deterministic partitioning contract. */
public interface DataSink {
    EventSinkProvider getEventSinkProvider();

    MetadataApplier getMetadataApplier();

    /**
     * Returns deterministic routing that keeps mutations of the same write identity together.
     *
     * <p>An UPDATE stays indivisible. Its before image, after image and complete event must
     * select the same writer; the runtime rejects a change crossing writer partitions.
     * Sinks accepting mutable write keys can preserve ordering by routing the whole table
     * to one writer, including earlier mutations and reuse of a previous key.
     */
    default HashFunctionProvider<DataChangeEvent> getDataChangeEventHashFunctionProvider() {
        return new DefaultDataChangeEventHashFunctionProvider();
    }

    default HashFunctionProvider<DataChangeEvent> getDataChangeEventHashFunctionProvider(int parallelism) {
        return getDataChangeEventHashFunctionProvider();
    }
}
