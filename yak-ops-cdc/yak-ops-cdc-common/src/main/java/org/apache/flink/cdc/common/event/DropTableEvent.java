package org.apache.flink.cdc.common.event;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.source.DataSource;

import java.util.Objects;

/**
 * A {@link SchemaChangeEvent} that represents an {@code DROP TABLE} DDL. this will be sent by
 * {@link DataSource} before all {@link DataChangeEvent} with the same tableId.
 */
@PublicEvolving
public class DropTableEvent implements SchemaChangeEvent {

    private static final long serialVersionUID = 1L;

    private final TableId tableId;

    public DropTableEvent(TableId tableId) {
        this.tableId = tableId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DropTableEvent)) {
            return false;
        }
        DropTableEvent that = (DropTableEvent) o;
        return Objects.equals(tableId, that.tableId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId);
    }

    @Override
    public String toString() {
        return "DropTableEvent{" + "tableId=" + tableId + '}';
    }

    @Override
    public TableId tableId() {
        return tableId;
    }

    @Override
    public SchemaChangeEventType getType() {
        return SchemaChangeEventType.DROP_TABLE;
    }

    @Override
    public SchemaChangeEvent copy(TableId newTableId) {
        return new DropTableEvent(newTableId);
    }
}
