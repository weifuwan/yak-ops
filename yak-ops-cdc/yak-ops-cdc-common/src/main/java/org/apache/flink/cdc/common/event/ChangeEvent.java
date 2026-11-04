package org.apache.flink.cdc.common.event;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/**
 * Class {@code ChangeEvent} represents the change events of external systems, including {@link
 * DataChangeEvent} and {@link SchemaChangeEvent}.
 */
@PublicEvolving
public interface ChangeEvent extends Event {

    /** Describes the database table corresponding to the occurrence of a change event. */
    TableId tableId();
}
