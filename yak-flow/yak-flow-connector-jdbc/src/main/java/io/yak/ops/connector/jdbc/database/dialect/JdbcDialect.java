package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Database-owned SQL planning contract for bounded reading, target DDL, and write statements.
 *
 * <p>Core owns logical types and table identifiers; this Connector owns SQL dialects.
 */
public interface JdbcDialect {

    String quoteIdentifier(String identifier);

    String qualifiedTable(TableId table);

    /** Applies JDBC cursor/connection defaults for a bounded, read-only source. */
    default void configureReadConnection(java.sql.Connection connection) throws java.sql.SQLException {
        connection.setReadOnly(true);
    }

    static String requireIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("SQL identifier must not be blank");
        }
        return identifier;
    }

    
    default JdbcNativeType nativeType(Column column) {
        throw new UnsupportedOperationException("This JDBC dialect has no target DDL type mapping");
    }

    
    default String createTableSql(TableId table, TableSchema schema) {
        String definitions = schema.columns().stream()
                .map(column -> {
                    String nullable = column.nullable() ? "" : " NOT NULL";
                    return quoteIdentifier(column.name()) + " "
                            + nativeType(column).ddl() + nullable;
                })
                .collect(Collectors.joining(", "));

        if (!schema.primaryKeys().isEmpty()) {
            String primaryKeys =
                    schema.primaryKeys().stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
            definitions += ", PRIMARY KEY (" + primaryKeys + ")";
        }
        return "CREATE TABLE " + qualifiedTable(table) + " (" + definitions + ")";
    }

    
    default JdbcDdlPlan createTablePlan(
            TableId table, TableSchema schema, String tableComment, Map<String, String> columnComments) {
        String createTableSql = createTableSql(table, schema);
        List<String> statements = new ArrayList<>();
        statements.add(createTableSql);

        if (hasComment(tableComment)) {
            statements.add("COMMENT ON TABLE " + qualifiedTable(table) + " IS " + stringLiteral(tableComment));
        }

        Map<String, String> comments = columnComments == null ? Map.of() : columnComments;
        for (Column column : schema.columns()) {
            String comment = comments.get(column.name());
            if (!hasComment(comment)) continue;
            statements.add("COMMENT ON COLUMN " + qualifiedTable(table) + "." + quoteIdentifier(column.name()) + " IS "
                    + stringLiteral(comment));
        }
        return new JdbcDdlPlan(createTableSql, statements);
    }

    default String stringLiteral(String value) {
        if (value == null) throw new IllegalArgumentException("SQL literal value must not be null");
        return "'" + value.replace("'", "''") + "'";
    }

    private boolean hasComment(String value) {
        return value != null && !value.isBlank();
    }

    default String selectSql(TableId table, TableSchema schema) {
        return selectSql(table, schema, null);
    }

    default String selectRangeSql(TableId table, TableSchema schema, String splitColumn) {
        requireSplitColumn(splitColumn);
        return selectSql(table, schema, splitColumn);
    }

    default String splitStatisticsSql(TableId table, String splitColumn) {
        requireSplitColumn(splitColumn);
        String column = quoteIdentifier(splitColumn);
        return "SELECT MIN(" + column + "), MAX(" + column + "), COUNT(*) FROM " + qualifiedTable(table);
    }

    default String truncateSql(TableId table) {
        return "TRUNCATE TABLE " + qualifiedTable(table);
    }

    default String upsertSql(TableId table, TableSchema schema) {
        throw new UnsupportedOperationException("UPSERT is not supported by this JDBC dialect");
    }

    default String insertSql(TableId table, TableSchema schema) {
        String columns = schema.columns().stream()
                .map(Column::name)
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String placeholders = schema.columns().stream().map(ignored -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + qualifiedTable(table) + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    default String deleteSql(TableId table, TableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("DELETE changelog requires primary key");
        }
        String predicate = schema.primaryKeys().stream()
                .map(primaryKey -> quoteIdentifier(primaryKey) + " = ?")
                .collect(Collectors.joining(" AND "));
        return "DELETE FROM " + qualifiedTable(table) + " WHERE " + predicate;
    }

    private void requireSplitColumn(String splitColumn) {
        if (splitColumn == null || splitColumn.isBlank()) {
            throw new IllegalArgumentException("splitColumn must not be blank");
        }
    }

    private String selectSql(TableId table, TableSchema schema, String splitColumn) {
        String columns = schema.columns().stream()
                .map(Column::name)
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String where = splitColumn == null
                ? ""
                : " WHERE " + quoteIdentifier(splitColumn) + " >= ? AND " + quoteIdentifier(splitColumn) + " <= ?";
        String orderBy = schema.primaryKeys().isEmpty()
                ? ""
                : " ORDER BY "
                        + schema.primaryKeys().stream()
                                .map(this::quoteIdentifier)
                                .collect(Collectors.joining(", "));
        return "SELECT " + columns + " FROM " + qualifiedTable(table) + where + orderBy;
    }
}
