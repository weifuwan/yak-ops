package org.apache.flink.cdc.runtime.operators.schema.distributed.event;

import org.apache.flink.cdc.common.event.SchemaChangeEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.runtime.operators.schema.distributed.SchemaCoordinator;
import org.apache.flink.runtime.operators.coordination.CoordinationResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Response from a {@link SchemaCoordinator} to broadcast a coordination consensus. */
public class SchemaChangeResponse implements CoordinationResponse {

    private final Map<TableId, Schema> evolvedSchemas;
    private final List<SchemaChangeEvent> evolvedSchemaChangeEvents;

    public SchemaChangeResponse(
            Map<TableId, Schema> evolvedSchemas,
            List<SchemaChangeEvent> evolvedSchemaChangeEvents) {
        this.evolvedSchemas = evolvedSchemas;
        this.evolvedSchemaChangeEvents = evolvedSchemaChangeEvents;
    }

    public Map<TableId, Schema> getEvolvedSchemas() {
        return evolvedSchemas;
    }

    public List<SchemaChangeEvent> getEvolvedSchemaChangeEvents() {
        return evolvedSchemaChangeEvents;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SchemaChangeResponse)) {
            return false;
        }
        SchemaChangeResponse that = (SchemaChangeResponse) o;
        return Objects.equals(evolvedSchemas, that.evolvedSchemas)
                && Objects.equals(evolvedSchemaChangeEvents, that.evolvedSchemaChangeEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(evolvedSchemas, evolvedSchemaChangeEvents);
    }

    @Override
    public String toString() {
        return "SchemaChangeResponse{"
                + "evolvedSchemas="
                + evolvedSchemas
                + ", evolvedSchemaChangeEvents="
                + evolvedSchemaChangeEvents
                + '}';
    }
}
