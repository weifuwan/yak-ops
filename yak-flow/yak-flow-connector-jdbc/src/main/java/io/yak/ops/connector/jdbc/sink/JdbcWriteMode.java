package io.yak.ops.connector.jdbc.sink;

/** JDBC write semantics; REPLACE is bounded MySQL replacement, RETRACT requires coordinated XA. */
public enum JdbcWriteMode {
    APPEND,
    UPSERT,
    REPLACE,
    RETRACT
}
