package io.yak.ops.connector.jdbc.database.internal.dialect;

import io.yak.ops.connector.jdbc.database.dialect.AbstractDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDdlPlan;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialectConverter;
import io.yak.ops.connector.jdbc.database.dialect.JdbcNativeType;
import io.yak.ops.connector.jdbc.database.internal.convert.MySqlJdbcDialectConverter;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements MySQL SQL generation, backtick quoting, and native target type mapping.
 *
 * <p>Catalog-qualified table names use the database namespace. String and binary columns
 * beyond conservative inline capacities map to LONGTEXT/LONGBLOB; these types are not
 * suitable for primary-key columns. Native UPSERT uses ON DUPLICATE KEY UPDATE, including
 * a no-op key assignment when no non-key columns exist.
 */
public final class MySqlJdbcDialect extends AbstractDialect {

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
                throw new UnsupportedOperationException("MySQL cannot preserve TIMESTAMP_WITH_TIME_ZONE semantics");
        };
    }

    @Override
    public JdbcDdlPlan createTablePlan(
            TableId table, TableSchema schema, String tableComment, Map<String, String> columnComments) {
        return createTablePlan(table, schema, tableComment, columnComments, Map.of());
    }

    @Override
    public JdbcDdlPlan createTablePlan(
            TableId table,
            TableSchema schema,
            String tableComment,
            Map<String, String> columnComments,
            Map<String, ColumnProperties> properties) {
        Map<String, String> comments = columnComments == null ? Map.of() : columnComments;
        String definitions = schema.columns().stream()
                .map(column -> {
                    String nullable = column.nullable() ? "" : " NOT NULL";
                    String comment = comments.get(column.name());
                    String commentClause =
                            comment == null || comment.isBlank() ? "" : " COMMENT " + stringLiteral(comment);
                    ColumnProperties attribute = properties.get(column.name());
                    String extras = renderProperties(column, schema, attribute);
                    return quoteIdentifier(column.name()) + " "
                            + nativeType(column).ddl() + nullable + extras + commentClause;
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

    /**
     * Preserve only metadata whose SQL semantics are known. A JDBC default is not trusted SQL:
     * numeric values are parsed, strings are quoted, and time functions are whitelisted.
     */
    private String renderProperties(Column column, TableSchema schema, ColumnProperties properties) {
        if (properties == null) return "";
        if (!properties.isKnown()) {
            throw new IllegalArgumentException("Unknown generation metadata for column: " + column.name());
        }
        if (Boolean.TRUE.equals(properties.generated())) {
            throw new IllegalArgumentException(
                    "Generated column requires a verified expression and write projection: " + column.name());
        }
        boolean identity = Boolean.TRUE.equals(properties.autoIncrement());
        String defaultValue = properties.defaultExpression();
        if (identity && defaultValue != null) {
            throw new IllegalArgumentException("Identity column also declares a default: " + column.name());
        }
        if (identity) {
            if (!schema.primaryKeys().contains(column.name())
                    || !java.util.Set.of("TINYINT", "SMALLINT", "INTEGER", "BIGINT")
                            .contains(column.dataType().getTypeRoot().name())) {
                throw new IllegalArgumentException("AUTO_INCREMENT requires an integer primary key: " + column.name());
            }
            return " AUTO_INCREMENT";
        }
        String update = properties.onUpdateExpression();
        String onUpdate = "";
        if (update != null) {
            if (!column.dataType().getTypeRoot().name().startsWith("TIMESTAMP")
                    || !update.trim().matches("(?i)CURRENT_TIMESTAMP(?:\\([0-6]?\\))?")) {
                throw new IllegalArgumentException("Unsupported ON UPDATE expression for column: " + column.name());
            }
            onUpdate = " ON UPDATE " + update.trim().toUpperCase(java.util.Locale.ROOT);
        }
        if (defaultValue == null) return onUpdate;
        if (properties.defaultGenerated() == null) {
            throw new IllegalArgumentException("Unknown default expression kind for column: " + column.name());
        }
        String value = defaultValue.trim();
        String root = column.dataType().getTypeRoot().name();
        if (Boolean.TRUE.equals(properties.defaultGenerated())
                && value.matches("(?i)CURRENT_TIMESTAMP(?:\\([0-6]?\\))?")
                && root.startsWith("TIMESTAMP")) {
            return " DEFAULT " + value.toUpperCase(java.util.Locale.ROOT) + onUpdate;
        }
        if (Boolean.TRUE.equals(properties.defaultGenerated())) {
            throw new IllegalArgumentException("Unsupported default expression for column: " + column.name());
        }
        if (java.util.Set.of("CHAR", "VARCHAR", "DATE", "TIME_WITHOUT_TIME_ZONE", "TIMESTAMP_WITHOUT_TIME_ZONE")
                .contains(root)) {
            // information_schema.COLUMN_DEFAULT is the unquoted literal for ordinary defaults.
            String literal = defaultValue;
            // SQL_MODE is connection-specific; do not guess how escaped literals will parse.
            if (literal.indexOf('\\') >= 0 || literal.chars().anyMatch(character -> character < 32)) {
                throw new IllegalArgumentException("Unsupported default literal escaping for column: " + column.name());
            }
            return " DEFAULT " + stringLiteral(literal) + onUpdate;
        }
        if (java.util.Set.of("TINYINT", "SMALLINT", "INTEGER", "BIGINT", "FLOAT", "DOUBLE", "DECIMAL", "BOOLEAN")
                        .contains(root)
                && value.matches("[-+]?[0-9]+(?:\\.[0-9]+)?")) {
            return " DEFAULT " + value + onUpdate;
        }
        throw new IllegalArgumentException("Unsupported default expression for column: " + column.name());
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
