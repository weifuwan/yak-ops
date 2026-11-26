package io.yak.ops.modeling.ddl.model;

/** Database-neutral logical field types accepted by the modeling DDL layer. */
public enum DdlDataType {
    TINYINT,
    SMALLINT,
    INT,
    BIGINT,
    BOOLEAN,
    CHAR,
    VARCHAR,
    STRING,
    TEXT,
    DECIMAL,
    DATE,
    DATETIME,
    TIMESTAMP,
    TIME,
    FLOAT,
    BINARY,
    VARBINARY,
    JSON,
    ARRAY,
    DOUBLE,
    MAP,
    STRUCT
}
