package io.yak.ops.flow.connector.jdbc.dialect;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.JdbcTargetTableDdlPlan;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MySQL JDBC SQL 标识符和表路径规则。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class MySqlJdbcDialect implements JdbcDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    public String qualifiedTable(DataSourceTablePath table) {
        if (table.database() == null || table.database().isBlank()) {
            return quoteIdentifier(table.table());
        }
        return quoteIdentifier(table.database()) + "." + quoteIdentifier(table.table());
    }

    @Override
    public JdbcNativeType nativeType(YakColumn column) {
        return switch (column.dataType().kind()) {
            case BOOLEAN -> JdbcNativeType.of("BOOLEAN");
            case TINYINT -> JdbcNativeType.of("TINYINT");
            case SMALLINT -> JdbcNativeType.of("SMALLINT");
            case INTEGER -> JdbcNativeType.of("INT");
            case BIGINT -> JdbcNativeType.of("BIGINT");
            case FLOAT -> JdbcNativeType.of("FLOAT");
            case DOUBLE -> JdbcNativeType.of("DOUBLE");
            case DECIMAL -> JdbcDialectTypeMappings.decimal(column, "DECIMAL", 65, 30);
            case STRING -> stringType(column.length());
            case BINARY -> binaryType(column.length());
            case DATE -> JdbcNativeType.of("DATE");
            case TIME -> JdbcNativeType.of("TIME(6)");
            case TIMESTAMP -> JdbcNativeType.of("DATETIME(6)");
            case TIMESTAMP_WITH_TIME_ZONE ->
                throw new UnsupportedOperationException("MySQL 无法保留 TIMESTAMP_WITH_TIME_ZONE 语义");
        };
    }

    @Override
    public JdbcTargetTableDdlPlan createTablePlan(
            DataSourceTablePath table, YakTableSchema schema, String tableComment, Map<String, String> columnComments) {
        Map<String, String> comments = columnComments == null ? Map.of() : columnComments;
        String definitions = schema.columns().stream()
                .map(column -> {
                    String nullable = column.nullable() ? "" : " NOT NULL";
                    String comment = comments.get(column.name());
                    String commentClause =
                            comment == null || comment.isBlank() ? "" : " COMMENT " + stringLiteral(comment);
                    return quoteIdentifier(column.name()) + " "
                            + nativeType(column).ddl() + nullable + commentClause;
                })
                .collect(Collectors.joining(", "));

        if (!schema.primaryKeys().isEmpty()) {
            String primaryKeys =
                    schema.primaryKeys().stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
            definitions += ", PRIMARY KEY (" + primaryKeys + ")";
        }

        String tableCommentClause =
                tableComment == null || tableComment.isBlank() ? "" : " COMMENT=" + stringLiteral(tableComment);
        String createTableSql = "CREATE TABLE " + qualifiedTable(table) + " (" + definitions + ")" + tableCommentClause;
        return new JdbcTargetTableDdlPlan(createTableSql, List.of(createTableSql));
    }

    private JdbcNativeType stringType(Integer length) {
        if (JdbcDialectTypeMappings.knownLength(length) && length <= 16383) {
            return JdbcNativeType.of("VARCHAR(" + length + ")");
        }
        String warning = JdbcDialectTypeMappings.knownLength(length)
                ? "STRING length=" + length + " 超过保守 VARCHAR 容量，目标使用 LONGTEXT"
                : "STRING length 未知，目标使用 LONGTEXT";
        return JdbcNativeType.nonKey("LONGTEXT", warning);
    }

    private JdbcNativeType binaryType(Integer length) {
        if (JdbcDialectTypeMappings.knownLength(length) && length <= 16383) {
            return JdbcNativeType.of("VARBINARY(" + length + ")");
        }
        String warning = JdbcDialectTypeMappings.knownLength(length)
                ? "BINARY length=" + length + " 超过保守 VARBINARY 容量，目标使用 LONGBLOB"
                : "BINARY length 未知，目标使用 LONGBLOB";
        return JdbcNativeType.nonKey("LONGBLOB", warning);
    }

    @Override
    public String upsertSql(DataSourceTablePath table, YakTableSchema schema) {
        requirePrimaryKey(schema);
        List<String> primaryKeys = schema.primaryKeys();
        List<YakColumn> updateColumns = schema.columns().stream()
                .filter(column -> !primaryKeys.contains(column.name()))
                .toList();
        String updateClause = updateColumns.isEmpty()
                ? noOpUpdate(primaryKeys.getFirst())
                : updateColumns.stream()
                        .map(column -> {
                            String name = quoteIdentifier(column.name());
                            return name + " = VALUES(" + name + ")";
                        })
                        .collect(Collectors.joining(", "));
        return insertSql(table, schema) + " ON DUPLICATE KEY UPDATE " + updateClause;
    }

    private String noOpUpdate(String primaryKey) {
        String name = quoteIdentifier(primaryKey);
        return name + " = VALUES(" + name + ")";
    }

    private void requirePrimaryKey(YakTableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("MySQL UPSERT requires primary key");
        }
    }
}
