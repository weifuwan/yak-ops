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
 * Reads an exact physical table's resolved schema using a caller-owned JDBC connection.
 *
 * <p>Metadata discovery uses escaped table name patterns and checks exact identifier matches.
 * The dialect converter resolves column types from a zero-row projection, while JDBC
 * primary-key metadata determines the stable KEY_SEQ order. This utility neither opens
 * another connection nor owns its caller's transaction.
 */
public final class JdbcTableMetadata {

    private JdbcTableMetadata() {}

    /**
     * Resolves catalog and schema defaults from the supplied JDBC connection.
     *
     * @param connection open connection used for all metadata operations
     * @param dialect vendor SQL quotation and type conversion rules
     * @param tableId exact physical table ID, optionally omitting catalog/schema
     * @return resolved column order, nullability and ordered primary keys
     * @throws SQLException if table discovery or type resolution fails
     */
    public static TableSchema readTable(Connection connection, JdbcDialect dialect, TableId tableId)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(tableId, "tableId");
        String catalog = tableId.catalog() == null ? connection.getCatalog() : tableId.catalog();
        String schema = tableId.schema() == null ? connection.getSchema() : tableId.schema();
        return readTable(connection, dialect, tableId, catalog, schema);
    }

    /**
     * Reads a table using an explicitly resolved catalog/schema namespace.
     *
     * <p>The namespace fields are used for exact metadata lookup, not for a wildcard scan.
     * Results fail when JDBC metadata is missing or returns an inconsistent key order.
     *
     * @param connection caller-owned JDBC connection
     * @param dialect vendor quotation and row-conversion rules
     * @param tableId exact table identity
     * @param catalog resolved catalog for metadata lookup
     * @param schema resolved schema/owner for metadata lookup
     * @return source/target table schema with ordered primary keys
     * @throws SQLException on missing or incompatible metadata
     */
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
