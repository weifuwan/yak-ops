package org.apache.flink.cdc.runtime.operators.schema.distributed.event;

import org.apache.flink.cdc.common.event.SchemaChangeEvent;
import org.apache.flink.cdc.common.utils.Preconditions;
import org.apache.flink.cdc.runtime.operators.schema.distributed.SchemaCoordinator;
import org.apache.flink.runtime.operators.coordination.CoordinationRequest;

/** Schema operator's request to {@link SchemaCoordinator} for merging an incompatible schema. */
public class SchemaChangeRequest implements CoordinationRequest {
    // Indicating which source subTask does this schema change event comes from.
    private final int sourceSubTaskId;

    // Indicating which schema mapper initiates this schema change request.
    private final int sinkSubTaskId;

    // A schema change event is uniquely bound to a sourceSubTaskId.
    private final SchemaChangeEvent schemaChangeEvent;

    public static SchemaChangeRequest createNoOpRequest(int sinkSubTaskId) {
        return new SchemaChangeRequest(-1, sinkSubTaskId, null);
    }

    public SchemaChangeRequest(
            int sourceSubTaskId, int sinkSubTaskId, SchemaChangeEvent schemaChangeEvent) {
        this.sourceSubTaskId = sourceSubTaskId;
        this.sinkSubTaskId = sinkSubTaskId;
        this.schemaChangeEvent = schemaChangeEvent;
    }

    // Checking if this schema request was invalidated since it has been submitted by another
    // downstream sink subTask before.
    public boolean isNoOpRequest() {
        return sourceSubTaskId == -1 || schemaChangeEvent == null;
    }

    public int getSourceSubTaskId() {
        Preconditions.checkState(
                !isNoOpRequest(), "Unable to fetch source subTaskId for an align event.");
        return sourceSubTaskId;
    }

    public int getSinkSubTaskId() {
        return sinkSubTaskId;
    }

    public SchemaChangeEvent getSchemaChangeEvent() {
        Preconditions.checkState(
                !isNoOpRequest(), "Unable to fetch source subTaskId for an align event.");
        return schemaChangeEvent;
    }

    @Override
    public String toString() {
        return "SchemaChangeRequest{"
                + "sourceSubTaskId="
                + sourceSubTaskId
                + ", sinkSubTaskId="
                + sinkSubTaskId
                + ", schemaChangeEvent="
                + schemaChangeEvent
                + '}';
    }
}
