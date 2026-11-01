package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.data.TableId;
import java.util.Objects;

/** Reader acknowledgment that the coordinated target and source schema are installed. */
public final class TableDiscoveryAckEvent implements SourceEvent {
    private static final long serialVersionUID = 1L;
    private final TableId table;

    public TableDiscoveryAckEvent(TableId table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    public TableId table() {
        return table;
    }
}
