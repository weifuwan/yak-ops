package io.yak.ops.connector.jdbc.database.internal.dialect;

import io.yak.ops.connector.jdbc.database.dialect.AbstractDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDdlPlan;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcNativeType;
import io.yak.ops.connector.jdbc.database.dialect.JdbcTypeConverter;
import io.yak.ops.connector.jdbc.database.internal.convert.MySqlTypeConverter;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
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
    public JdbcTypeConverter typeConverter() {
        return new MySqlTypeConverter();
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
    public List<String> addColumnStatements(TableId table, PhysicalColumn column) {
        if (!column.type().isResolved() || !column.type().isNullable()) {
            throw new IllegalArgumentException("ADD COLUMN requires a resolved nullable type: " + column.name());
        }
        ColumnProperties properties = column.properties();
        if (properties != null && (!properties.isKnown() || properties.hasFeatures())) {
            throw new IllegalArgumentException("ADD COLUMN cannot preserve column expressions: " + column.name());
        }
        if (properties != null && !supportsNativeColumnAddition(properties.nativeType())) {
            throw new IllegalArgumentException("ADD COLUMN cannot preserve native type: " + column.name());
        }
        Column target = new Column(column.name(), column.type());
        String definition = quoteIdentifier(column.name()) + " " + validatedColumnType(target, properties) + " NULL";
        String comment = column.comment();
        if (comment != null && !comment.isBlank()) {
            definition += " COMMENT " + stringLiteral(comment);
        }
        return List.of("ALTER TABLE " + qualifiedTable(table) + " ADD COLUMN " + definition);
    }

    /**
     * Native types that carry semantics beyond their YakFlow logical type must not be
     * silently replaced by a generic type during schema evolution.
     */
    public static boolean requiresExactNativeType(String nativeType) {
        if (nativeType == null) return false;
        String type = nativeType.trim().toLowerCase(java.util.Locale.ROOT);
        return type.startsWith("enum(")
                || type.startsWith("set(")
                || type.equals("json")
                || type.equals("year")
                || type.startsWith("year(")
                || type.equals("point")
                || type.contains("unsigned")
                || type.contains("zerofill");
    }

    private static boolean supportsNativeColumnAddition(String nativeType) {
        if (!requiresExactNativeType(nativeType)) return true;
        String type = nativeType.trim().toLowerCase(java.util.Locale.ROOT);
        return type.startsWith("enum(") || type.startsWith("set(") || type.equals("json");
    }

    @Override
    public String dropColumnSql(TableId table, String column) {
        return "ALTER TABLE " + qualifiedTable(table) + " DROP COLUMN " + quoteIdentifier(column);
    }

    @Override
    public String alterColumnTypeSql(TableId table, Column previous, Column updated, String comment) {
        if (!previous.name().equals(updated.name()) || previous.nullable() != updated.nullable()) {
            throw new IllegalArgumentException("Column identity and nullability must remain unchanged");
        }
        String suffix = updated.nullable() ? " NULL" : " NOT NULL";
        if (comment != null && !comment.isBlank()) {
            suffix += " COMMENT " + stringLiteral(comment);
        }
        return "ALTER TABLE " + qualifiedTable(table) + " MODIFY COLUMN " + quoteIdentifier(updated.name()) + " "
                + nativeType(updated).ddl() + suffix;
    }

    @Override
    public LogicalType toTargetLogicalType(Column column) {
        JdbcNativeType targetType = nativeType(column);
        boolean nullable = column.nullable();
        return switch (column.dataType().getTypeRoot()) {
            case BOOLEAN -> LogicalTypes.TINYINT.copy(nullable);
            case VARCHAR ->
                "LONGTEXT".equals(targetType.ddl()) ? LogicalTypes.STRING.copy(nullable) : column.dataType();
            case VARBINARY ->
                "LONGBLOB".equals(targetType.ddl()) ? LogicalTypes.BINARY.copy(nullable) : column.dataType();
            default -> column.dataType();
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
                    return quoteIdentifier(column.name()) + " " + validatedColumnType(column, attribute) + nullable
                            + extras + commentClause;
                })
                .collect(Collectors.joining(", "));

        if (!schema.primaryKeys().isEmpty()) {
            String primaryKeys =
                    schema.primaryKeys().stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
            definitions += ", PRIMARY KEY (" + primaryKeys + ")";
        }

        String tableCommentClause =
                tableComment == null || tableComment.isBlank() ? "" : " COMMENT=" + stringLiteral(tableComment);
        String createTableSql = "CREATE TABLE " + qualifiedTable(table) + " (" + definitions + ")" + tableCommentClause
                + " ENGINE=InnoDB";
        return new JdbcDdlPlan(createTableSql, List.of(createTableSql));
    }

    /**
     * Native MySQL types are preserved only when the server-reported COLUMN_TYPE
     * matches an explicit SQL grammar. Never interpolate arbitrary metadata into DDL.
     */
    private String validatedColumnType(Column column, ColumnProperties properties) {
        if (properties == null || properties.nativeType() == null) {
            return nativeType(column).ddl();
        }
        String type = properties.nativeType().trim();
        if (type.chars().anyMatch(character -> character < 32)
                || type.indexOf('\\') >= 0
                || type.contains(";")
                || type.contains("--")
                || type.contains("/*")) {
            throw new IllegalArgumentException("Unsupported MySQL native type for column: " + column.name());
        }
        String standard = "(?i)(?:tinyint|smallint|mediumint|int|integer|bigint|decimal|numeric|float|double|real"
                + "|char|varchar|binary|varbinary|bit|date|time|datetime|timestamp|year"
                + "|tinytext|text|mediumtext|longtext|tinyblob|blob|mediumblob|longblob|json|point)"
                + "(?:\\([0-9]{1,6}(?:\\s*,\\s*[0-9]{1,6})?\\))?"
                + "(?:\\s+unsigned)?(?:\\s+zerofill)?";
        String enumerated = "(?i)(?:enum|set)\\('(?:[^'\\\\]|'')*'(?:\\s*,\\s*'(?:[^'\\\\]|'')*')*\\)";
        if (!type.matches(standard) && !type.matches(enumerated)) {
            throw new IllegalArgumentException("Unsupported MySQL native type for column: " + column.name());
        }
        return type;
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
            if ("DATE".equals(root)) {
                // MySQL 8 expression defaults: only deterministic, bounded grammar is allowed.
                var dateExpression = java.util.regex.Pattern.compile(
                                "(?i)^\\(*\\s*curdate\\(\\)\\s*(?:\\+\\s*interval\\s+([1-9][0-9]{0,2})\\s+day)?\\s*\\)*$")
                        .matcher(value);
                if (dateExpression.matches()) {
                    String interval = dateExpression.group(1);
                    return " DEFAULT (CURDATE()" + (interval == null ? "" : " + INTERVAL " + interval + " DAY") + ")"
                            + onUpdate;
                }
            }
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

    /**
     * MySQL's null-safe comparison matches nullable before-images, and LIMIT 1
     * retracts only one physical occurrence of a duplicate full row.
     */
    @Override
    public String exactRowDeleteSql(TableId table, TableSchema schema) {
        if (!schema.primaryKeys().isEmpty() || schema.columnCount() == 0) {
            throw new IllegalArgumentException("MySQL exact-row DELETE requires a nonempty keyless table");
        }
        String predicate = schema.columns().stream()
                .map(column -> quoteIdentifier(column.name()) + " <=> ?")
                .collect(Collectors.joining(" AND "));
        return "DELETE FROM " + qualifiedTable(table) + " WHERE " + predicate + " LIMIT 1";
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

    @Override
    public String replaceSql(TableId table, TableSchema schema) {
        String columns = schema.columns().stream()
                .map(column -> quoteIdentifier(column.name()))
                .collect(Collectors.joining(", "));
        String values = schema.columns().stream().map(column -> "?").collect(Collectors.joining(", "));
        return "REPLACE INTO " + qualifiedTable(table) + " (" + columns + ") VALUES (" + values + ")";
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
