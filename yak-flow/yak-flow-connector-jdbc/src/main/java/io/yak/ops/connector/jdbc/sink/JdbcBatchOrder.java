package io.yak.ops.connector.jdbc.sink;

/** Physical ordering of one bounded Writer batch; not a persisted task write mode. */
public enum JdbcBatchOrder {
    /** Preserve the complete input order, including dependencies between tables. */
    INPUT_ORDER,
    /** Group explicitly independent tables while preserving all operations within each table. */
    INDEPENDENT_TABLES
}
