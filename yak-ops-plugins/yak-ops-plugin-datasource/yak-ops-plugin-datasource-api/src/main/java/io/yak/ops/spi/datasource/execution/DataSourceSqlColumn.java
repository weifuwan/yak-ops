package io.yak.ops.spi.datasource.execution;

/** JDBC-neutral column metadata returned by a SQL execution. */
public record DataSourceSqlColumn(String name, String label, String typeName, int jdbcType, boolean nullable) {}
