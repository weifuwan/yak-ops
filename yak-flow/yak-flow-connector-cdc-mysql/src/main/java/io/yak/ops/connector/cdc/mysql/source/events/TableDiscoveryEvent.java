package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.Schema;
import java.util.Objects;

/** Coordinator-owned candidate whose target must be prepared before snapshot assignment. */
public final class TableDiscoveryEvent implements SourceEvent {
    private static final long serialVersionUID = 1L;
    private final TableId table;
    private final Schema schema;

    public TableDiscoveryEvent(TableId table, Schema schema) {
        this.table = Objects.requireNonNull(table, "table");
        this.schema = Objects.requireNonNull(schema, "schema");
    }

    public TableId table() {
        return table;
    }

    public Schema schema() {
        return schema;
    }

    public String eventId() {
        String catalog = table.catalog();
        return "table-discovery:" + catalog.length() + ":" + catalog + ":"
                + table.table().length() + ":" + table.table();
    }
}
