package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/** An enumeration of data type families for clustering {@link DataTypeRoot}s into categories. */
@PublicEvolving
public enum DataTypeFamily {
    PREDEFINED,

    CONSTRUCTED,

    USER_DEFINED,

    CHARACTER_STRING,

    BINARY_STRING,

    NUMERIC,

    INTEGER_NUMERIC,

    EXACT_NUMERIC,

    APPROXIMATE_NUMERIC,

    DATETIME,

    TIME,

    TIMESTAMP,

    INTERVAL,

    COLLECTION,

    EXTENSION
}
