package io.yak.ops.connector.jdbc.database.dialect;

import io.yak.ops.connector.jdbc.database.internal.convert.StandardJdbcDialectConverter;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides vendor-neutral DML and DDL rendering shared by {@link JdbcDialect} implementations.
 *
 * <p>The base quotes each identifier through the vendor contract and uses schema column
 * order for SELECT/INSERT and primary-key order for DELETE. Vendors override native types,
 * identifier namespaces, and UPSERT syntax. A dialect does not execute statements or manage
 * JDBC transactions.
 */
public abstract class AbstractDialect implements JdbcDialect {

    @Override
    public JdbcDialectConverter createRowConverter(TableSchema schema) {
        return new StandardJdbcDialectConverter(schema);
    }

    @Override
    public JdbcDialectConverter createRowConverter(ResultSetMetaData metadata) throws SQLException {
        return new StandardJdbcDialectConverter(metadata);
    }

    @Override
    public void configureReadConnection(Connection connection) throws SQLException {
        connection.setReadOnly(true);
    }

    @Override
    public JdbcNativeType nativeType(Column column) {
        throw new UnsupportedOperationException("This JDBC dialect has no target DDL type mapping");
    }

    @Override
    public String createTableSql(TableId table, TableSchema schema) {
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

    @Override
    public JdbcDdlPlan createTablePlan(
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

    @Override
    public String stringLiteral(String value) {
        if (value == null) throw new IllegalArgumentException("SQL literal value must not be null");
        return "'" + value.replace("'", "''") + "'";
    }

    private boolean hasComment(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String selectSql(TableId table, TableSchema schema) {
        return selectSql(table, schema, null);
    }

    @Override
    public String selectRangeSql(TableId table, TableSchema schema, String splitColumn) {
        requireSplitColumn(splitColumn);
        return selectSql(table, schema, splitColumn);
    }

    @Override
    public String splitStatisticsSql(TableId table, String splitColumn) {
        requireSplitColumn(splitColumn);
        String column = quoteIdentifier(splitColumn);
        return "SELECT MIN(" + column + "), MAX(" + column + "), COUNT(*) FROM " + qualifiedTable(table);
    }

    @Override
    public String truncateSql(TableId table) {
        return "TRUNCATE TABLE " + qualifiedTable(table);
    }

    @Override
    public String upsertSql(TableId table, TableSchema schema) {
        throw new UnsupportedOperationException("UPSERT is not supported by this JDBC dialect");
    }

    @Override
    public String insertSql(TableId table, TableSchema schema) {
        String columns = schema.columns().stream()
                .map(Column::name)
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String placeholders = schema.columns().stream().map(ignored -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + qualifiedTable(table) + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    @Override
    public String deleteSql(TableId table, TableSchema schema) {
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
