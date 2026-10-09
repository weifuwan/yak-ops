package io.yak.ops.core.types;

/**
 * The subset of Flink-style logical type roots currently understood by YakFlow.
 *
 * <p>Vendor SQL type names and JDBC {@code java.sql.Types} codes are not logical type roots.
 */
public enum LogicalTypeRoot {
    BOOLEAN,
    TINYINT,
    SMALLINT,
    INTEGER,
    BIGINT,
    FLOAT,
    DOUBLE,
    DECIMAL,
    CHAR,
    VARCHAR,
    BINARY,
    VARBINARY,
    DATE,
    TIME_WITHOUT_TIME_ZONE,
    TIMESTAMP_WITHOUT_TIME_ZONE,
    TIMESTAMP_WITH_TIME_ZONE
}
