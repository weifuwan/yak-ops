package io.yak.ops.connector.base.sink;

import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.sink.DataSink;
import io.yak.ops.core.sink.EventSinkProvider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Event and metadata entry points backed by the connector's single writer implementation. */
public final class TableDataSink implements DataSink {
    private final EventSinkProvider provider;
    private final Supplier<? extends MetadataApplier> metadata;

    public TableDataSink(
            Sink<TableRecord> sink,
            List<TableSinkFactoryContext> tables,
            Supplier<? extends MetadataApplier> metadata) {
        Sink<Event> eventSink = TableEventSink.of(sink, tables);
        this.provider = () -> eventSink;
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    public TableDataSink(
            Sink<TableRecord> sink,
            List<TableSinkFactoryContext> tables,
            Supplier<? extends MetadataApplier> metadata,
            BiFunction<TableId, Schema, Schema> sourceSchemaTransform,
            Map<TableId, Schema> sourceSchemas) {
        Sink<Event> eventSink = TableEventSink.of(sink, tables, sourceSchemaTransform, sourceSchemas);
        this.provider = () -> eventSink;
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    @Override
    public EventSinkProvider getEventSinkProvider() {
        return provider;
    }

    @Override
    public MetadataApplier getMetadataApplier() {
        return Objects.requireNonNull(metadata.get(), "metadata");
    }
}
