package org.apache.flink.cdc.common.event;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.io.Serializable;

/**
 * Class {@code SchemaChangeEvent} represents the changes in the table structure of the external
 * system, such as CREATE, DROP, RENAME and so on.
 */
@PublicEvolving
public interface SchemaChangeEvent extends ChangeEvent, Serializable {
    /** Returns its {@link SchemaChangeEventType}. */
    SchemaChangeEventType getType();

    /** Creates a copy of {@link SchemaChangeEvent} with new {@link TableId}. */
    SchemaChangeEvent copy(TableId newTableId);
}
