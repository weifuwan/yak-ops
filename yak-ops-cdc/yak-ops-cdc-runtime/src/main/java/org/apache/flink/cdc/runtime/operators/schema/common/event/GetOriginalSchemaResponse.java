package org.apache.flink.cdc.runtime.operators.schema.common.event;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.runtime.operators.schema.common.SchemaRegistry;
import org.apache.flink.runtime.operators.coordination.CoordinationResponse;

import javax.annotation.Nullable;

import java.util.Optional;

/** Coordination response from {@link SchemaRegistry} for {@link GetOriginalSchemaRequest}. */
@Internal
public class GetOriginalSchemaResponse implements CoordinationResponse {
    @Nullable private final Schema schema;

    public GetOriginalSchemaResponse(@Nullable Schema schema) {
        this.schema = schema;
    }

    public Optional<Schema> getSchema() {
        return Optional.ofNullable(schema);
    }
}
