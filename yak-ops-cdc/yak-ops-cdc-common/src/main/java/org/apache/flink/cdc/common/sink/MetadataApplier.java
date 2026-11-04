package org.apache.flink.cdc.common.sink;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.event.SchemaChangeEvent;
import org.apache.flink.cdc.common.event.SchemaChangeEventType;
import org.apache.flink.cdc.common.event.SchemaChangeEventTypeFamily;
import org.apache.flink.cdc.common.exceptions.SchemaEvolveException;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** {@code MetadataApplier} is used to apply metadata changes to external systems. */
@PublicEvolving
public interface MetadataApplier extends Serializable, AutoCloseable {

    /** Apply the given {@link SchemaChangeEvent} to external systems. */
    void applySchemaChange(SchemaChangeEvent schemaChangeEvent) throws SchemaEvolveException;

    /** Returns optional support for safely expanding an existing target table schema. */
    @Experimental
    default Optional<ExistingTableSchemaExpansionSupport> getExistingTableSchemaExpansionSupport() {
        return Optional.empty();
    }

    /** Sets enabled schema evolution event types of current metadata applier. */
    default MetadataApplier setAcceptedSchemaEvolutionTypes(
            Set<SchemaChangeEventType> schemaEvolutionTypes) {
        return this;
    }

    /** Checks if this metadata applier should this event type. */
    default boolean acceptsSchemaEvolutionType(SchemaChangeEventType schemaChangeEventType) {
        return true;
    }

    /** Checks what kind of schema change events downstream can handle. */
    default Set<SchemaChangeEventType> getSupportedSchemaEvolutionTypes() {
        return Arrays.stream(SchemaChangeEventTypeFamily.ALL).collect(Collectors.toSet());
    }

    /** Closes the metadata applier and its underlying resources. */
    @Override
    default void close() throws Exception {}
}
