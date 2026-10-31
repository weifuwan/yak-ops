package io.yak.ops.connector.base.source;

import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.source.DataSource;
import io.yak.ops.core.source.EventSourceProvider;
import io.yak.ops.core.source.MetadataAccessor;
import java.util.Map;
import java.util.Objects;

/** Exposes a connector's table reader as an Event source with scoped metadata. */
public final class TableDataSource implements DataSource {
    private final EventSourceProvider provider;
    private final MetadataAccessor metadata;

    public TableDataSource(Source<TableRecord, ?, ?> source, Map<TableId, Schema> schemas) {
        this.metadata = new TableMetadataAccessor(schemas);
        Source<io.yak.ops.core.event.Event, ?, ?> events =
                new TableEventSource<>(Objects.requireNonNull(source), schemas);
        this.provider = () -> events;
    }

    @Override
    public EventSourceProvider getEventSourceProvider() {
        return provider;
    }

    @Override
    public MetadataAccessor getMetadataAccessor() {
        return metadata;
    }
}
