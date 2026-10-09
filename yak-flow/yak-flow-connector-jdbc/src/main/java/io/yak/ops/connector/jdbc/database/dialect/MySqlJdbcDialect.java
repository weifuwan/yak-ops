package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.connector.jdbc.database.converter.JdbcDialectConverter;
import io.yak.ops.connector.jdbc.database.converter.MySqlJdbcDialectConverter;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** MySQL quoting, native type mapping, and DDL planning. */
public final class MySqlJdbcDialect implements JdbcDialect {

    @Override
    public JdbcDialectConverter createRowConverter(java.sql.ResultSetMetaData metadata) throws java.sql.SQLException {
        return new MySqlJdbcDialectConverter(metadata);
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + JdbcDialect.requireIdentifier(identifier).replace("`", "``") + "`";
    }

    @Override
    public String qualifiedTable(TableId table) {
        if (table.catalog() == null || table.catalog().isBlank()) {
            return quoteIdentifier(table.table());
        }
        return quoteIdentifier(table.catalog()) + "." + quoteIdentifier(table.table());
    }

    @Override
    public JdbcNativeType nativeType(Column column) {
        return switch (column.dataType().getTypeRoot()) {
            case BOOLEAN -> JdbcNativeType.of("BOOLEAN");
            case TINYINT -> JdbcNativeType.of("TINYINT");
            case SMALLINT -> JdbcNativeType.of("SMALLINT");
            case INTEGER -> JdbcNativeType.of("INT");
            case BIGINT -> JdbcNativeType.of("BIGINT");
            case FLOAT -> JdbcNativeType.of("FLOAT");
            case DOUBLE -> JdbcNativeType.of("DOUBLE");
            case DECIMAL -> JdbcTypeMappings.decimal(column, "DECIMAL", 65, 30);
            case CHAR -> JdbcNativeType.of("CHAR(" + column.length() + ")");
            case VARCHAR -> stringType(column.length());
            case BINARY -> JdbcNativeType.of("BINARY(" + column.length() + ")");
            case VARBINARY -> binaryType(column.length());
            case DATE -> JdbcNativeType.of("DATE");
            case TIME_WITHOUT_TIME_ZONE ->
                JdbcNativeType.of("TIME(" + JdbcTypeMappings.precision(column, 6, "MySQL TIME") + ")");
            case TIMESTAMP_WITHOUT_TIME_ZONE ->
                JdbcNativeType.of("DATETIME(" + JdbcTypeMappings.precision(column, 6, "MySQL DATETIME") + ")");
            case TIMESTAMP_WITH_TIME_ZONE ->
                throw new UnsupportedOperationException("MySQL 无法保留 TIMESTAMP_WITH_TIME_ZONE 语义");
        };
    }

    @Override
    public JdbcDdlPlan createTablePlan(
            TableId table, TableSchema schema, String tableComment, Map<String, String> columnComments) {
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
        return new JdbcDdlPlan(createTableSql, List.of(createTableSql));
    }

    private JdbcNativeType stringType(Integer length) {
        if (JdbcTypeMappings.knownLength(length) && length <= 16383) {
            return JdbcNativeType.of("VARCHAR(" + length + ")");
        }
        String warning = JdbcTypeMappings.knownLength(length)
                ? "STRING length=" + length + " 超过保守 VARCHAR 容量，目标使用 LONGTEXT"
                : "STRING length 未知，目标使用 LONGTEXT";
        return JdbcNativeType.nonKey("LONGTEXT", warning);
    }

    private JdbcNativeType binaryType(Integer length) {
        if (JdbcTypeMappings.knownLength(length) && length <= 16383) {
            return JdbcNativeType.of("VARBINARY(" + length + ")");
        }
        String warning = JdbcTypeMappings.knownLength(length)
                ? "BINARY length=" + length + " 超过保守 VARBINARY 容量，目标使用 LONGBLOB"
                : "BINARY length 未知，目标使用 LONGBLOB";
        return JdbcNativeType.nonKey("LONGBLOB", warning);
    }

    @Override
    public String upsertSql(TableId table, TableSchema schema) {
        requirePrimaryKey(schema);
        List<String> primaryKeys = schema.primaryKeys();
        List<Column> updateColumns = schema.columns().stream()
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

    private void requirePrimaryKey(TableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("MySQL UPSERT requires primary key");
        }
    }
}
