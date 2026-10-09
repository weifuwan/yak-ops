package io.yak.ops.core.types;

/** Database-independent logical type roots used by schema planning and data conversion. */
public enum TypeKind {
    BOOLEAN,
    TINYINT,
    SMALLINT,
    INTEGER,
    BIGINT,
    FLOAT,
    DOUBLE,
    DECIMAL,
    STRING,
    BINARY,
    DATE,
    TIME,
    TIMESTAMP,
    TIMESTAMP_WITH_TIME_ZONE
}
