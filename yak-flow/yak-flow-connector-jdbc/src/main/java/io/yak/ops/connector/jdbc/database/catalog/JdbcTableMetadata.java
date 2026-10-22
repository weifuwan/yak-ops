package io.yak.ops.connector.jdbc.database.catalog;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Reads exact JDBC table descriptions and logical schema using a caller-owned Connection.
 *
 * <p>Native metadata is shared by Datasource browsing and Connector planning. Dialect
 * conversion remains the sole owner of logical column types. This utility never opens
 * a second Connection or takes ownership of the caller's transaction.
 */
public final class JdbcTableMetadata {

    private JdbcTableMetadata() {}

    /**
     * Resolves a table's logical schema using connection defaults.
     *
     * @param connection open caller-owned Connection
     * @param dialect vendor dialect and row converter
     * @param tableId physical table path
     * @return ordered column schema with stable primary-key order
     * @throws SQLException on missing or unreadable metadata
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
     * Resolves a table with an explicitly selected JDBC namespace.
     *
     * @param connection open caller-owned Connection
     * @param dialect vendor-specific SQL quotation and type conversion
     * @param tableId exact table identity
     * @param catalog resolved catalog for metadata lookup
     * @param schema resolved schema for metadata lookup
     * @return resolved column schema and ordered primary keys
     * @throws SQLException if metadata is missing or inconsistent
     */
    public static TableSchema readTable(
            Connection connection, JdbcDialect dialect, TableId tableId, String catalog, String schema)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(tableId, "tableId");
        if (findTableInfo(connection, tableId, catalog, schema).isEmpty()) {
            throw new SQLException("JDBC table does not exist or is not visible: " + tableId.table());
        }

        String sql = "SELECT * FROM " + dialect.qualifiedTable(tableId) + " WHERE 1 = 0";
        TableSchema projected;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            projected = dialect.createRowConverter(rows.getMetaData()).schema();
        }
        TreeMap<Integer, String> keys = readPrimaryKeys(connection.getMetaData(), catalog, schema, tableId.table());
        return new TableSchema(projected.columns(), List.copyOf(keys.values()));
    }

    /**
     * Finds the full physical table identity and its native type/comment.
     *
     * @param connection open caller-owned Connection
     * @param tableId exact table identity
     * @param catalog resolved catalog
     * @param schema resolved schema
     * @return the physical table or view when visible
     * @throws SQLException if metadata lookup fails
     */
    public static Optional<JdbcTableInfo> findTableInfo(
            Connection connection, TableId tableId, String catalog, String schema) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(tableId, "tableId");
        DatabaseMetaData metadata = connection.getMetaData();
        String pattern = AbstractJdbcCatalog.escapePattern(metadata, tableId.table());
        try (ResultSet tables = metadata.getTables(catalog, schema, pattern, new String[] {"TABLE", "VIEW"})) {
            while (tables.next()) {
                String actualName = tables.getString("TABLE_NAME");
                String actualCatalog = tables.getString("TABLE_CAT");
                String actualSchema = tables.getString("TABLE_SCHEM");
                if (tableId.table().equals(actualName)
                        && (catalog == null || catalog.equals(actualCatalog))
                        && (schema == null || schema.equals(actualSchema))) {
                    return Optional.of(new JdbcTableInfo(
                            new TableId(actualCatalog, actualSchema, actualName),
                            tables.getString("TABLE_TYPE"),
                            tables.getString("REMARKS")));
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Lists native columns without inventing JDBC to logical type mappings.
     *
     * @param connection open caller-owned Connection
     * @param tableId exact table identity
     * @param catalog resolved catalog
     * @param schema resolved schema
     * @return physical columns in ORDINAL_POSITION order, including KEY_SEQ
     * @throws SQLException when JDBC metadata is absent or inconsistent
     */
    public static List<JdbcColumnInfo> readColumns(
            Connection connection, TableId tableId, String catalog, String schema) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(tableId, "tableId");
        if (findTableInfo(connection, tableId, catalog, schema).isEmpty()) {
            throw new SQLException("JDBC table does not exist or is not visible: " + tableId.table());
        }
        DatabaseMetaData metadata = connection.getMetaData();
        TreeMap<Integer, String> keys = readPrimaryKeys(metadata, catalog, schema, tableId.table());
        Map<String, Integer> keySequenceByName = new java.util.HashMap<>();
        for (Map.Entry<Integer, String> entry : keys.entrySet()) {
            keySequenceByName.put(entry.getValue(), entry.getKey());
        }
        TreeMap<Integer, JdbcColumnInfo> columns = new TreeMap<>();
        String pattern = AbstractJdbcCatalog.escapePattern(metadata, tableId.table());
        try (ResultSet result = metadata.getColumns(catalog, schema, pattern, "%")) {
            while (result.next()) {
                String name = result.getString("COLUMN_NAME");
                if (!tableId.table().equals(result.getString("TABLE_NAME")) || name == null) {
                    continue;
                }
                int position = result.getInt("ORDINAL_POSITION");
                if (position < 1) {
                    throw new SQLException("Invalid JDBC column ordinal");
                }
                Integer keyPosition = keySequenceByName.get(name);
                JdbcColumnInfo column = new JdbcColumnInfo(
                        name,
                        result.getString("TYPE_NAME"),
                        result.getInt("DATA_TYPE"),
                        nullableInteger(result, "COLUMN_SIZE"),
                        nullableInteger(result, "DECIMAL_DIGITS"),
                        result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        position,
                        keyPosition != null,
                        keyPosition,
                        result.getString("REMARKS"),
                        result.getString("COLUMN_DEF") != null,
                        metadataFlag(result.getString("IS_AUTOINCREMENT")),
                        metadataFlag(result.getString("IS_GENERATEDCOLUMN")),
                        result.getString("COLUMN_DEF"));
                if (columns.putIfAbsent(position, column) != null) {
                    throw new SQLException("Duplicate JDBC column ordinal");
                }
            }
        }
        if (columns.isEmpty()) {
            throw new SQLException("No columns discovered for JDBC table: " + tableId.table());
        }
        List<JdbcColumnInfo> ordered = new ArrayList<>(columns.values());
        for (int position = 0; position < ordered.size(); position++) {
            if (ordered.get(position).ordinalPosition() != position + 1) {
                throw new SQLException("Inconsistent JDBC column ordering");
            }
        }
        for (String key : keys.values()) {
            if (ordered.stream().noneMatch(column -> column.name().equals(key))) {
                throw new SQLException("JDBC primary key refers to an unknown column");
            }
        }
        if ("MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
            return readMySqlUpdates(connection, catalog, tableId.table(), ordered);
        }
        return List.copyOf(ordered);
    }

    /**
     * JDBC COLUMN_DEF does not contain MySQL's ON UPDATE clause. Read the server's
     * canonical EXTRA metadata separately, using bound schema/table identifiers.
     */
    private static List<JdbcColumnInfo> readMySqlUpdates(
            Connection connection, String catalog, String table, List<JdbcColumnInfo> columns) throws SQLException {
        Map<String, String> updates = new HashMap<>();
        Map<String, Boolean> generatedDefaults = new HashMap<>();
        Map<String, Boolean> generatedColumns = new HashMap<>();
        Map<String, String> defaults = new HashMap<>();
        String sql = "SELECT COLUMN_NAME, EXTRA, COLUMN_DEFAULT FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, catalog == null ? connection.getCatalog() : catalog);
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String extra = rows.getString("EXTRA");
                    if (extra == null) throw new SQLException("MySQL column EXTRA metadata is unavailable");
                    String flags = extra.toLowerCase(java.util.Locale.ROOT);
                    String name = rows.getString("COLUMN_NAME");
                    generatedDefaults.put(name, flags.contains("default_generated"));
                    // MySQL JDBC may report DEFAULT_GENERATED as IS_GENERATEDCOLUMN. The physical
                    // generated-column markers are distinct from generated DEFAULT expressions.
                    generatedColumns.put(
                            name, flags.contains("virtual generated") || flags.contains("stored generated"));
                    defaults.put(name, rows.getString("COLUMN_DEFAULT"));
                    if (!flags.contains("on update")) continue;
                    var matcher = java.util.regex.Pattern.compile(
                                    "(?i)on update\\s+(current_timestamp(?:\\([0-6]?\\))?)")
                            .matcher(extra);
                    updates.put(name, matcher.find() ? matcher.group(1) : "UNSUPPORTED_ON_UPDATE");
                }
            }
        }
        List<JdbcColumnInfo> enriched = new ArrayList<>(columns.size());
        for (JdbcColumnInfo column : columns) {
            if (!generatedDefaults.containsKey(column.name())) {
                throw new SQLException("MySQL physical default metadata is incomplete");
            }
            enriched.add(new JdbcColumnInfo(
                    column.name(),
                    column.typeName(),
                    column.jdbcType(),
                    column.size(),
                    column.scale(),
                    column.nullable(),
                    column.ordinalPosition(),
                    column.primaryKey(),
                    column.primaryKeyPosition(),
                    column.remarks(),
                    defaults.get(column.name()) != null,
                    column.autoIncrement(),
                    generatedColumns.get(column.name()),
                    defaults.get(column.name()),
                    updates.get(column.name()),
                    generatedDefaults.get(column.name())));
        }
        return List.copyOf(enriched);
    }

    private static TreeMap<Integer, String> readPrimaryKeys(
            DatabaseMetaData metadata, String catalog, String schema, String table) throws SQLException {
        TreeMap<Integer, String> keys = new TreeMap<>();
        try (ResultSet result = metadata.getPrimaryKeys(catalog, schema, table)) {
            while (result.next()) {
                int sequence = result.getInt("KEY_SEQ");
                String name = result.getString("COLUMN_NAME");
                if (sequence < 1 || name == null || keys.putIfAbsent(sequence, name) != null) {
                    throw new SQLException("Inconsistent JDBC primary-key ordering");
                }
            }
        }
        for (int expected = 1; expected <= keys.size(); expected++) {
            if (!keys.containsKey(expected)) {
                throw new SQLException("Non-contiguous JDBC primary-key sequence");
            }
        }
        return keys;
    }

    private static Boolean metadataFlag(String value) {
        if ("YES".equalsIgnoreCase(value)) return true;
        if ("NO".equalsIgnoreCase(value)) return false;
        return null;
    }

    private static Integer nullableInteger(ResultSet result, String name) throws SQLException {
        int value = result.getInt(name);
        return result.wasNull() ? null : value;
    }
}
