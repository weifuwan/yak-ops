package io.yak.ops.plugin.datasource.mysql.catalog;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.sql.Connection;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * MySQL metadata provided by the installed MySQL JDBC driver.
 */
public class MySQLDataSourceCatalog implements DataSourceCatalog {

    private static final String JDBC_URL = "jdbc:mysql://127.0.0.1:3306/yak_ops";
    private static final String TABLE_FILTER =
            " FROM information_schema.TABLES"
                    + " WHERE TABLE_SCHEMA = ?"
                    + " AND TABLE_TYPE IN ('BASE TABLE', 'VIEW')";

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
                        property.choices == null ? Collections.emptyList() : Arrays.asList(property.choices)));
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
        String filter = hasKeyword(keyword) ? TABLE_FILTER + " AND INSTR(TABLE_NAME, ?) > 0" : TABLE_FILTER;
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*)" + filter)) {
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
    public List<String> listTables(
            Connection connection, String database, String keyword, int offset, int limit) {
        String filter = hasKeyword(keyword) ? TABLE_FILTER + " AND INSTR(TABLE_NAME, ?) > 0" : TABLE_FILTER;
        String sql = "SELECT TABLE_NAME" + filter + " ORDER BY TABLE_NAME LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, database);
            if (hasKeyword(keyword)) {
                statement.setString(index++, keyword.trim());
            }
            statement.setInt(index++, limit);
            statement.setInt(index, offset);
            try (ResultSet result = statement.executeQuery()) {
                List<String> tables = new ArrayList<>();
                while (result.next()) {
                    tables.add(result.getString(1));
                }
                return tables;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list MySQL tables", e);
        }
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
