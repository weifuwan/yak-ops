package io.yak.ops.plugin.datasource.mysql.catalog;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalogColumn;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalogTable;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MySQL metadata provided by the installed MySQL JDBC driver.
 */
public class MySQLDataSourceCatalog implements DataSourceCatalog {

    private static final String JDBC_URL = "jdbc:mysql://127.0.0.1:3306/yak_ops";
    private static final String TABLE_FILTER =
            " FROM information_schema.TABLES t"
                    + " WHERE t.TABLE_SCHEMA = ?"
                    + " AND t.TABLE_TYPE IN ('BASE TABLE', 'VIEW')";

    @Override
    public List<ConnectionProperty> getConnectionProperties() {
        try {
            DriverPropertyInfo[] properties = new com.mysql.cj.jdbc.Driver()
                    .getPropertyInfo(JDBC_URL, new Properties());
            List<ConnectionProperty> result = new ArrayList<>();
            for (DriverPropertyInfo property : properties) {
                if (property.name == null || isHidden(property.name)) {
                    continue;
                }
                result.add(new ConnectionProperty(
                        property.name,
                        property.description == null ? "" : property.description,
                        property.value,
                        property.required,
                        property.choices == null
                                ? Collections.emptyList()
                                : Arrays.asList(property.choices)));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read MySQL JDBC properties", e);
        }
    }

    @Override
    public boolean supportsTablePaging() {
        return true;
    }

    @Override
    public int countTables(Connection connection, String database, String keyword) {
        String filter = hasKeyword(keyword)
                ? TABLE_FILTER + " AND INSTR(t.TABLE_NAME, ?) > 0"
                : TABLE_FILTER;
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT COUNT(*)" + filter)) {
            statement.setString(1, database);
            if (hasKeyword(keyword)) {
                statement.setString(2, keyword.trim());
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count MySQL tables", e);
        }
    }

    @Override
    public List<DataSourceCatalogTable> listTables(
            Connection connection, String database, String keyword, int offset, int limit) {
        String keywordFilter = hasKeyword(keyword) ? " AND INSTR(t.TABLE_NAME, ?) > 0" : "";
        String sql = "SELECT t.TABLE_NAME, t.TABLE_TYPE, t.TABLE_COMMENT,"
                + " GROUP_CONCAT(k.COLUMN_NAME ORDER BY k.ORDINAL_POSITION SEPARATOR ',') AS PRIMARY_KEYS"
                + " FROM information_schema.TABLES t"
                + " LEFT JOIN information_schema.KEY_COLUMN_USAGE k"
                + " ON k.TABLE_SCHEMA = t.TABLE_SCHEMA"
                + " AND k.TABLE_NAME = t.TABLE_NAME"
                + " AND k.CONSTRAINT_NAME = 'PRIMARY'"
                + " WHERE t.TABLE_SCHEMA = ?"
                + " AND t.TABLE_TYPE IN ('BASE TABLE', 'VIEW')"
                + keywordFilter
                + " GROUP BY t.TABLE_NAME, t.TABLE_TYPE, t.TABLE_COMMENT"
                + " ORDER BY t.TABLE_NAME LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, database);
            if (hasKeyword(keyword)) {
                statement.setString(index++, keyword.trim());
            }
            statement.setInt(index++, limit);
            statement.setInt(index, offset);
            try (ResultSet result = statement.executeQuery()) {
                return readTables(result);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list MySQL tables", e);
        }
    }

    @Override
    public List<DataSourceCatalogTable> getTables(
            Connection connection, String database, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = tableNames.stream().map(ignored -> "?").collect(Collectors.joining(","));
        String sql = "SELECT t.TABLE_NAME, t.TABLE_TYPE, t.TABLE_COMMENT,"
                + " GROUP_CONCAT(k.COLUMN_NAME ORDER BY k.ORDINAL_POSITION SEPARATOR ',') AS PRIMARY_KEYS"
                + " FROM information_schema.TABLES t"
                + " LEFT JOIN information_schema.KEY_COLUMN_USAGE k"
                + " ON k.TABLE_SCHEMA = t.TABLE_SCHEMA"
                + " AND k.TABLE_NAME = t.TABLE_NAME"
                + " AND k.CONSTRAINT_NAME = 'PRIMARY'"
                + " WHERE t.TABLE_SCHEMA = ?"
                + " AND t.TABLE_NAME IN (" + placeholders + ")"
                + " AND t.TABLE_TYPE IN ('BASE TABLE', 'VIEW')"
                + " GROUP BY t.TABLE_NAME, t.TABLE_TYPE, t.TABLE_COMMENT"
                + " ORDER BY t.TABLE_NAME";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, database);
            for (String tableName : tableNames) {
                statement.setString(index++, tableName);
            }
            try (ResultSet result = statement.executeQuery()) {
                return readTables(result);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load MySQL table metadata", e);
        }
    }

    @Override
    public List<DataSourceCatalogColumn> listColumns(
            Connection connection, String database, String tableName) {
        try {
            DatabaseMetaData metadata = connection.getMetaData();
            Set<String> primaryKeys = new HashSet<>();
            try (ResultSet result = metadata.getPrimaryKeys(database, null, tableName)) {
                while (result.next()) {
                    primaryKeys.add(result.getString("COLUMN_NAME"));
                }
            }

            List<DataSourceCatalogColumn> columns = new ArrayList<>();
            try (ResultSet result = metadata.getColumns(database, null, tableName, "%")) {
                while (result.next()) {
                    String name = result.getString("COLUMN_NAME");
                    columns.add(new DataSourceCatalogColumn(
                            name,
                            result.getString("TYPE_NAME"),
                            result.getInt("DATA_TYPE"),
                            result.getInt("COLUMN_SIZE"),
                            result.getInt("DECIMAL_DIGITS"),
                            result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                            result.getInt("ORDINAL_POSITION"),
                            primaryKeys.contains(name),
                            result.getString("REMARKS")));
                }
            }
            return columns;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load MySQL column metadata", e);
        }
    }

    private static List<DataSourceCatalogTable> readTables(ResultSet result) throws SQLException {
        List<DataSourceCatalogTable> tables = new ArrayList<>();
        while (result.next()) {
            String primaryKeys = result.getString("PRIMARY_KEYS");
            tables.add(new DataSourceCatalogTable(
                    result.getString("TABLE_NAME"),
                    result.getString("TABLE_TYPE"),
                    result.getString("TABLE_COMMENT"),
                    primaryKeys == null || primaryKeys.isBlank()
                            ? Collections.emptyList()
                            : Arrays.asList(primaryKeys.split(","))));
        }
        return tables;
    }

    private static boolean hasKeyword(String keyword) {
        return keyword != null && !keyword.trim().isEmpty();
    }

    private static boolean isHidden(String name) {
        return "user".equalsIgnoreCase(name)
                || "password".equalsIgnoreCase(name)
                || "allowLoadLocalInfile".equalsIgnoreCase(name)
                || "allowLocalInfile".equalsIgnoreCase(name)
                || "allowUrlInLocalInfile".equalsIgnoreCase(name)
                || "autoDeserialize".equalsIgnoreCase(name);
    }
}
