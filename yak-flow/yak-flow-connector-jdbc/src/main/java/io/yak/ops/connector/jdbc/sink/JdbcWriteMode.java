package io.yak.ops.connector.jdbc.sink;

/** JDBC append-only and primary-key-aware changelog write modes. */
public enum JdbcWriteMode {
    APPEND,
    UPSERT
}
