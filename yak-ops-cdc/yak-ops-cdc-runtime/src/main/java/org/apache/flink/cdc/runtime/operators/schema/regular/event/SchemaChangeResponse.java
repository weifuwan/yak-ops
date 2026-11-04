package org.apache.flink.cdc.runtime.operators.schema.regular.event;

import org.apache.flink.cdc.common.event.SchemaChangeEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.runtime.operators.schema.regular.SchemaCoordinator;
import org.apache.flink.cdc.runtime.operators.schema.regular.SchemaOperator;
import org.apache.flink.runtime.operators.coordination.CoordinationResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The response for {@link SchemaChangeRequest} from {@link SchemaCoordinator} to {@link
 * SchemaOperator}.
 */
public class SchemaChangeResponse implements CoordinationResponse {
    private static final long serialVersionUID = 1L;

    /**
     * Actually finished schema change events. This will only be effective if status is {@code
     * accepted}.
     */
    private final List<SchemaChangeEvent> appliedSchemaChangeEvents;

    private final Map<TableId, Schema> evolvedSchemas;

    public SchemaChangeResponse(
            List<SchemaChangeEvent> appliedSchemaChangeEvents,
            Map<TableId, Schema> evolvedSchemas) {
        this.appliedSchemaChangeEvents = appliedSchemaChangeEvents;
        this.evolvedSchemas = evolvedSchemas;
    }

    public List<SchemaChangeEvent> getAppliedSchemaChangeEvents() {
        return appliedSchemaChangeEvents;
    }

    public Map<TableId, Schema> getEvolvedSchemas() {
        return evolvedSchemas;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SchemaChangeResponse)) {
            return false;
        }
        SchemaChangeResponse response = (SchemaChangeResponse) o;
        return Objects.equals(appliedSchemaChangeEvents, response.appliedSchemaChangeEvents)
                && Objects.equals(evolvedSchemas, response.evolvedSchemas);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appliedSchemaChangeEvents, evolvedSchemas);
    }

    @Override
    public String toString() {
        return "SchemaChangeResponse{"
                + "appliedSchemaChangeEvents="
                + appliedSchemaChangeEvents
                + ", evolvedSchemas="
                + evolvedSchemas
                + '}';
    }
}
