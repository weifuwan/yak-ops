package io.yak.ops.plugin.database.jdbc.schema.dialect;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PostgreSQL JDBC SQL 标识符和 Schema 表路径规则。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class PostgreSqlJdbcDialect implements JdbcDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String qualifiedTable(DataSourceTablePath table) {
        if (table.schema() == null || table.schema().isBlank()) {
            return quoteIdentifier(table.table());
        }
        return quoteIdentifier(table.schema()) + "." + quoteIdentifier(table.table());
    }

    @Override
    public JdbcNativeType nativeType(YakColumn column) {
        return switch (column.dataType().kind()) {
            case BOOLEAN -> JdbcNativeType.of("BOOLEAN");
            case TINYINT, SMALLINT -> JdbcNativeType.of("SMALLINT");
            case INTEGER -> JdbcNativeType.of("INTEGER");
            case BIGINT -> JdbcNativeType.of("BIGINT");
            case FLOAT -> JdbcNativeType.of("REAL");
            case DOUBLE -> JdbcNativeType.of("DOUBLE PRECISION");
            case DECIMAL -> JdbcDialectTypeMappings.decimal(column, "NUMERIC", 1000, 1000);
            case STRING -> stringType(column.length());
            case BINARY -> JdbcNativeType.of("BYTEA");
            case DATE -> JdbcNativeType.of("DATE");
            case TIME -> JdbcNativeType.of("TIME");
            case TIMESTAMP -> JdbcNativeType.of("TIMESTAMP");
            case TIMESTAMP_WITH_TIME_ZONE -> JdbcNativeType.of("TIMESTAMP WITH TIME ZONE");
        };
    }

    private JdbcNativeType stringType(Integer length) {
        if (JdbcDialectTypeMappings.knownLength(length)) {
            return JdbcNativeType.of("VARCHAR(" + length + ")");
        }
        return JdbcNativeType.of("TEXT", "STRING length 未知，目标使用 TEXT");
    }

    @Override
    public String upsertSql(DataSourceTablePath table, YakTableSchema schema) {
        requirePrimaryKey(schema);
        List<String> primaryKeys = schema.primaryKeys();
        String conflictColumns = primaryKeys.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        List<YakColumn> updateColumns = schema.columns().stream()
                .filter(column -> !primaryKeys.contains(column.name()))
                .toList();
        if (updateColumns.isEmpty()) {
            return insertSql(table, schema) + " ON CONFLICT (" + conflictColumns + ") DO NOTHING";
        }
        String updateClause = updateColumns.stream()
                .map(column -> {
                    String name = quoteIdentifier(column.name());
                    return name + " = EXCLUDED." + name;
                })
                .collect(Collectors.joining(", "));
        return insertSql(table, schema) + " ON CONFLICT (" + conflictColumns + ") DO UPDATE SET " + updateClause;
    }

    private void requirePrimaryKey(YakTableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("PostgreSQL UPSERT requires primary key");
        }
    }
}
