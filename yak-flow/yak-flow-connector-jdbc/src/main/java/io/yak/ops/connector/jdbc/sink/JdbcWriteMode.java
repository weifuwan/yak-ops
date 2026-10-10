package io.yak.ops.connector.jdbc.sink;

/** Supported single-table JDBC writing semantics. Changelog DELETE is not supported here. */
public enum JdbcWriteMode {
    APPEND,
    UPSERT
}
