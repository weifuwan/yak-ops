package org.apache.flink.cdc.common.event;

import java.util.List;
import java.util.Objects;

/**
 * An {@link Event} from {@code SchemaOperator} to notify {@code DataSinkWriterOperator} that it
 * start flushing.
 */
public class FlushEvent implements Event {
    /** The sink table(s) that need to be flushed. */
    private final List<TableId> tableIds;

    /** Which subTask ID this FlushEvent was initiated from. */
    private final int sourceSubTaskId;

    /** Which type of schema change event caused this FlushEvent. */
    private final SchemaChangeEventType schemaChangeEventType;

    public FlushEvent(
            int sourceSubTaskId,
            List<TableId> tableIds,
            SchemaChangeEventType schemaChangeEventType) {
        this.tableIds = tableIds;
        this.sourceSubTaskId = sourceSubTaskId;
        this.schemaChangeEventType = schemaChangeEventType;
    }

    public List<TableId> getTableIds() {
        return tableIds;
    }

    public int getSourceSubTaskId() {
        return sourceSubTaskId;
    }

    public SchemaChangeEventType getSchemaChangeEventType() {
        return schemaChangeEventType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FlushEvent)) {
            return false;
        }
        FlushEvent that = (FlushEvent) o;
        return sourceSubTaskId == that.sourceSubTaskId
                && Objects.equals(tableIds, that.tableIds)
                && Objects.equals(schemaChangeEventType, that.schemaChangeEventType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceSubTaskId);
    }

    @Override
    public String toString() {
        return "FlushEvent{"
                + "sourceSubTaskId="
                + sourceSubTaskId
                + ", tableIds="
                + tableIds
                + ", schemaChangeEventType="
                + schemaChangeEventType
                + '}';
    }
}
