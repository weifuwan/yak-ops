package org.apache.flink.cdc.common.event;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/**
 * Enum {@code OperationType} describes the type of operation that the data change event reports.
 */
@PublicEvolving
public enum OperationType {
    INSERT,
    UPDATE,
    REPLACE,
    DELETE
}
