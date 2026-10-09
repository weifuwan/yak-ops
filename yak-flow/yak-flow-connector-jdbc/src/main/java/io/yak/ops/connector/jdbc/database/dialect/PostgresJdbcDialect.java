package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.stream.Collectors;

/** PostgreSQL quoting, native type mapping, and DDL planning. */
public final class PostgresJdbcDialect implements JdbcDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + JdbcDialect.requireIdentifier(identifier).replace("\"", "\"\"") + "\"";
    }

    @Override
    public String qualifiedTable(TableId table) {
        if (table.schema() == null || table.schema().isBlank()) {
            return quoteIdentifier(table.table());
        }
        return quoteIdentifier(table.schema()) + "." + quoteIdentifier(table.table());
    }

    @Override
    public void configureReadConnection(java.sql.Connection connection) throws java.sql.SQLException {
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
    }

    @Override
    public JdbcNativeType nativeType(Column column) {
        return switch (column.dataType().kind()) {
            case BOOLEAN -> JdbcNativeType.of("BOOLEAN");
            case TINYINT, SMALLINT -> JdbcNativeType.of("SMALLINT");
            case INTEGER -> JdbcNativeType.of("INTEGER");
            case BIGINT -> JdbcNativeType.of("BIGINT");
            case FLOAT -> JdbcNativeType.of("REAL");
            case DOUBLE -> JdbcNativeType.of("DOUBLE PRECISION");
            case DECIMAL -> JdbcTypeMappings.decimal(column, "NUMERIC", 1000, 1000);
            case STRING -> stringType(column.length());
            case BINARY -> JdbcNativeType.of("BYTEA");
            case DATE -> JdbcNativeType.of("DATE");
            case TIME -> JdbcNativeType.of("TIME");
            case TIMESTAMP -> JdbcNativeType.of("TIMESTAMP");
            case TIMESTAMP_WITH_TIME_ZONE -> JdbcNativeType.of("TIMESTAMP WITH TIME ZONE");
        };
    }

    private JdbcNativeType stringType(Integer length) {
        if (JdbcTypeMappings.knownLength(length)) {
            return JdbcNativeType.of("VARCHAR(" + length + ")");
        }
        return JdbcNativeType.of("TEXT", "STRING length 未知，目标使用 TEXT");
    }

    @Override
    public String upsertSql(TableId table, TableSchema schema) {
        requirePrimaryKey(schema);
        List<String> primaryKeys = schema.primaryKeys();
        String conflictColumns = primaryKeys.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        List<Column> updateColumns = schema.columns().stream()
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

    private void requirePrimaryKey(TableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("PostgreSQL UPSERT requires primary key");
        }
    }
}
