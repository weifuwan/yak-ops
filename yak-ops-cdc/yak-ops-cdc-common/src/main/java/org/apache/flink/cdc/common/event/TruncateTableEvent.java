package org.apache.flink.cdc.common.event;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.source.DataSource;

import java.util.Objects;

/**
 * A {@link SchemaChangeEvent} that represents an {@code TRUNCATE TABLE} DDL. this will be sent by
 * {@link DataSource} before all {@link DataChangeEvent} with the same tableId.
 */
@PublicEvolving
public class TruncateTableEvent implements SchemaChangeEvent {

    private static final long serialVersionUID = 1L;

    private final TableId tableId;

    public TruncateTableEvent(TableId tableId) {
        this.tableId = tableId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TruncateTableEvent)) {
            return false;
        }
        TruncateTableEvent that = (TruncateTableEvent) o;
        return Objects.equals(tableId, that.tableId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId);
    }

    @Override
    public String toString() {
        return "TruncateTableEvent{" + "tableId=" + tableId + '}';
    }

    @Override
    public TableId tableId() {
        return tableId;
    }

    @Override
    public SchemaChangeEventType getType() {
        return SchemaChangeEventType.TRUNCATE_TABLE;
    }

    @Override
    public SchemaChangeEvent copy(TableId newTableId) {
        return new TruncateTableEvent(newTableId);
    }
}
