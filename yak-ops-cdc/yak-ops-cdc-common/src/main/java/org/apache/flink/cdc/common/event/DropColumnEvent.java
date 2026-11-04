package org.apache.flink.cdc.common.event;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.util.List;
import java.util.Objects;

/**
 * A {@link SchemaChangeEvent} that represents an {@code DROP COLUMN} DDL, which may contain the
 * lenient column type changes.
 */
@PublicEvolving
public class DropColumnEvent implements SchemaChangeEvent {

    private static final long serialVersionUID = 1L;

    private final TableId tableId;

    private final List<String> droppedColumnNames;

    public DropColumnEvent(TableId tableId, List<String> droppedColumnNames) {
        this.tableId = tableId;
        this.droppedColumnNames = droppedColumnNames;
    }

    public List<String> getDroppedColumnNames() {
        return droppedColumnNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DropColumnEvent)) {
            return false;
        }
        DropColumnEvent that = (DropColumnEvent) o;
        return Objects.equals(tableId, that.tableId)
                && Objects.equals(droppedColumnNames, that.droppedColumnNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId, droppedColumnNames);
    }

    @Override
    public String toString() {
        return "DropColumnEvent{"
                + "tableId="
                + tableId
                + ", droppedColumnNames="
                + droppedColumnNames
                + '}';
    }

    @Override
    public TableId tableId() {
        return tableId;
    }

    @Override
    public SchemaChangeEventType getType() {
        return SchemaChangeEventType.DROP_COLUMN;
    }

    @Override
    public SchemaChangeEvent copy(TableId newTableId) {
        return new DropColumnEvent(newTableId, droppedColumnNames);
    }
}
