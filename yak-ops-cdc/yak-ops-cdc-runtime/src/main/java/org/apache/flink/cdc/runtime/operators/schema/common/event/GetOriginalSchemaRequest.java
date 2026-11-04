package org.apache.flink.cdc.runtime.operators.schema.common.event;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.runtime.operators.schema.common.SchemaRegistry;
import org.apache.flink.runtime.operators.coordination.CoordinationRequest;

/** Request to {@link SchemaRegistry} for getting original schema of a table. */
@Internal
public class GetOriginalSchemaRequest implements CoordinationRequest {
    public static final int LATEST_SCHEMA_VERSION = -1;

    private final TableId tableId;
    private final int schemaVersion;

    public static GetOriginalSchemaRequest ofLatestSchema(TableId tableId) {
        return new GetOriginalSchemaRequest(tableId, LATEST_SCHEMA_VERSION);
    }

    public GetOriginalSchemaRequest(TableId tableId, int schemaVersion) {
        this.tableId = tableId;
        this.schemaVersion = schemaVersion;
    }

    public TableId getTableId() {
        return tableId;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    @Override
    public String toString() {
        return "GetOriginalSchemaRequest{"
                + "tableId="
                + tableId
                + ", schemaVersion="
                + schemaVersion
                + '}';
    }
}
