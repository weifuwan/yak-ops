package org.apache.flink.cdc.runtime.operators.schema.regular.event;

import org.apache.flink.cdc.common.event.SchemaChangeEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.runtime.operators.schema.regular.SchemaCoordinator;
import org.apache.flink.cdc.runtime.operators.schema.regular.SchemaOperator;
import org.apache.flink.runtime.operators.coordination.CoordinationRequest;

import java.util.Objects;

/**
 * The request from {@link SchemaOperator} to {@link SchemaCoordinator} to request to change
 * schemas.
 */
public class SchemaChangeRequest implements CoordinationRequest {

    private static final long serialVersionUID = 1L;

    /** The sender of the request. */
    private final TableId tableId;

    /** The schema changes. */
    private final SchemaChangeEvent schemaChangeEvent;

    /** The ID of subTask that initiated the request. */
    private final int subTaskId;

    public SchemaChangeRequest(
            TableId tableId, SchemaChangeEvent schemaChangeEvent, int subTaskId) {
        this.tableId = tableId;
        this.schemaChangeEvent = schemaChangeEvent;
        this.subTaskId = subTaskId;
    }

    public TableId getTableId() {
        return tableId;
    }

    public SchemaChangeEvent getSchemaChangeEvent() {
        return schemaChangeEvent;
    }

    public int getSubTaskId() {
        return subTaskId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SchemaChangeRequest)) {
            return false;
        }
        SchemaChangeRequest that = (SchemaChangeRequest) o;
        return Objects.equals(tableId, that.tableId)
                && Objects.equals(schemaChangeEvent, that.schemaChangeEvent)
                && subTaskId == that.subTaskId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId, schemaChangeEvent, subTaskId);
    }

    @Override
    public String toString() {
        return "SchemaChangeRequest{"
                + "tableId="
                + tableId
                + ", schemaChangeEvent="
                + schemaChangeEvent
                + ", subTaskId="
                + subTaskId
                + '}';
    }
}
