package io.yak.ops.plugin.database.jdbc.schema.dialect;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Oracle JDBC SQL 标识符和 Schema 表路径规则。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class OracleJdbcDialect implements JdbcDialect {

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
            case BOOLEAN -> JdbcNativeType.of("NUMBER(1)");
            case TINYINT -> JdbcNativeType.of("NUMBER(3)");
            case SMALLINT -> JdbcNativeType.of("NUMBER(5)");
            case INTEGER -> JdbcNativeType.of("NUMBER(10)");
            case BIGINT -> JdbcNativeType.of("NUMBER(19)");
            case FLOAT -> JdbcNativeType.of("BINARY_FLOAT");
            case DOUBLE -> JdbcNativeType.of("BINARY_DOUBLE");
            case DECIMAL -> JdbcDialectTypeMappings.decimal(column, "NUMBER", 38, 38);
            case STRING -> stringType(column.length());
            case BINARY -> binaryType(column.length());
            case DATE -> JdbcNativeType.of("DATE");
            case TIME -> throw new UnsupportedOperationException("Oracle 没有独立 TIME 列类型");
            case TIMESTAMP -> JdbcNativeType.of("TIMESTAMP(6)");
            case TIMESTAMP_WITH_TIME_ZONE -> JdbcNativeType.of("TIMESTAMP(6) WITH TIME ZONE");
        };
    }

    private JdbcNativeType stringType(Integer length) {
        if (JdbcDialectTypeMappings.knownLength(length) && length <= 4000) {
            return JdbcNativeType.of("VARCHAR2(" + length + " CHAR)");
        }
        String warning = JdbcDialectTypeMappings.knownLength(length)
                ? "STRING length=" + length + " 超过 VARCHAR2(4000 CHAR)，目标使用 CLOB"
                : "STRING length 未知，目标使用 CLOB";
        return JdbcNativeType.nonKey("CLOB", warning);
    }

    private JdbcNativeType binaryType(Integer length) {
        if (JdbcDialectTypeMappings.knownLength(length) && length <= 2000) {
            return JdbcNativeType.of("RAW(" + length + ")");
        }
        String warning = JdbcDialectTypeMappings.knownLength(length)
                ? "BINARY length=" + length + " 超过 RAW(2000)，目标使用 BLOB"
                : "BINARY length 未知，目标使用 BLOB";
        return JdbcNativeType.nonKey("BLOB", warning);
    }

    @Override
    public String upsertSql(DataSourceTablePath table, YakTableSchema schema) {
        requirePrimaryKey(schema);
        List<String> primaryKeys = schema.primaryKeys();
        List<YakColumn> updateColumns = schema.columns().stream()
                .filter(column -> !primaryKeys.contains(column.name()))
                .toList();
        String sourceProjection = schema.columns().stream()
                .map(column -> "? AS " + quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        String matchPredicate = primaryKeys.stream()
                .map(primaryKey -> "t." + quoteIdentifier(primaryKey) + " = s." + quoteIdentifier(primaryKey))
                .collect(Collectors.joining(" AND "));
        String insertColumns = schema.columns().stream()
                .map(YakColumn::name)
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String insertValues = schema.columns().stream()
                .map(column -> "s." + quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));

        StringBuilder sql = new StringBuilder()
                .append("MERGE INTO ")
                .append(qualifiedTable(table))
                .append(" t USING (SELECT ")
                .append(sourceProjection)
                .append(" FROM DUAL) s ON (")
                .append(matchPredicate)
                .append(") ");
        if (!updateColumns.isEmpty()) {
            sql.append("WHEN MATCHED THEN UPDATE SET ")
                    .append(updateColumns.stream()
                            .map(column -> {
                                String name = quoteIdentifier(column.name());
                                return "t." + name + " = s." + name;
                            })
                            .collect(Collectors.joining(", ")))
                    .append(" ");
        }
        return sql.append("WHEN NOT MATCHED THEN INSERT (")
                .append(insertColumns)
                .append(") VALUES (")
                .append(insertValues)
                .append(")")
                .toString();
    }

    private void requirePrimaryKey(YakTableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("Oracle UPSERT requires primary key");
        }
    }
}
