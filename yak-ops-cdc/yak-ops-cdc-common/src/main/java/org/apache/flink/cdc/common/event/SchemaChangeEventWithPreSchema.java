package org.apache.flink.cdc.common.event;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.schema.Schema;

/** A {@link SchemaChangeEvent} that supports appending schema before change event. */
@PublicEvolving
public interface SchemaChangeEventWithPreSchema extends SchemaChangeEvent {

    /** Describes if this event already has schema before change info. */
    boolean hasPreSchema();

    /** Append schema before change info to this event. */
    void fillPreSchema(Schema oldSchema);

    /** Check if this event contains redundant schema change request only. */
    default boolean trimRedundantChanges() {
        return false;
    }
}
