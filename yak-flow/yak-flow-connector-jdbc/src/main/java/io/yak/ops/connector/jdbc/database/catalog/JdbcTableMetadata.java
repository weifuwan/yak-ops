package io.yak.ops.connector.jdbc.database.catalog;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Resolves exact table metadata on a caller-owned JDBC connection.
 *
 * <p>Catalog and Source planning share one implementation of identifier lookup, column
 * conversion, and primary-key ordering without creating additional connections.
 */
public final class JdbcTableMetadata {

    private JdbcTableMetadata() {}

    public static TableSchema readTable(Connection connection, JdbcDialect dialect, TableId tableId)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(tableId, "tableId");
        String catalog = tableId.catalog() == null ? connection.getCatalog() : tableId.catalog();
        String schema = tableId.schema() == null ? connection.getSchema() : tableId.schema();
        return readTable(connection, dialect, tableId, catalog, schema);
    }

    public static TableSchema readTable(
            Connection connection, JdbcDialect dialect, TableId tableId, String catalog, String schema)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(tableId, "tableId");
        DatabaseMetaData metadata = connection.getMetaData();
        String pattern = AbstractJdbcCatalog.escapePattern(metadata, tableId.table());
        boolean found = false;
        try (ResultSet tables = metadata.getTables(catalog, schema, pattern, new String[] {"TABLE", "VIEW"})) {
            while (tables.next()) {
                if (tableId.table().equals(tables.getString("TABLE_NAME"))
                        && (catalog == null || catalog.equals(tables.getString("TABLE_CAT")))
                        && (schema == null || schema.equals(tables.getString("TABLE_SCHEM")))) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            throw new SQLException("JDBC table does not exist or is not visible: " + tableId.table());
        }

        String sql = "SELECT * FROM " + dialect.qualifiedTable(tableId) + " WHERE 1 = 0";
        TableSchema projected;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            projected = dialect.createRowConverter(rows.getMetaData()).schema();
        }

        TreeMap<Integer, String> keys = new TreeMap<>();
        try (ResultSet primaryKeys = metadata.getPrimaryKeys(catalog, schema, tableId.table())) {
            while (primaryKeys.next()) {
                int sequence = primaryKeys.getInt("KEY_SEQ");
                String name = primaryKeys.getString("COLUMN_NAME");
                if (sequence < 1 || name == null || keys.putIfAbsent(sequence, name) != null) {
                    throw new SQLException("Inconsistent JDBC primary-key ordering");
                }
            }
        }
        return new TableSchema(projected.columns(), List.copyOf(keys.values()));
    }
}
