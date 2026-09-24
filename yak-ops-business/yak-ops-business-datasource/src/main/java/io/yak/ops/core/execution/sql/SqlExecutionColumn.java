package io.yak.ops.core.execution.sql;

/** JDBC-neutral column metadata exposed by the platform SQL execution contract. */
public record SqlExecutionColumn(
    String name,
    String label,
    String typeName,
    int jdbcType,
    boolean nullable) {
}
