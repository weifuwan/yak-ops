package io.yak.ops.plugin.datasource.mysql.catalog;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.sql.DriverPropertyInfo;
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

    private static boolean isHidden(String name) {
        return "user".equalsIgnoreCase(name)
                || "password".equalsIgnoreCase(name)
                || "allowLoadLocalInfile".equalsIgnoreCase(name)
                || "allowLocalInfile".equalsIgnoreCase(name)
                || "allowUrlInLocalInfile".equalsIgnoreCase(name)
                || "autoDeserialize".equalsIgnoreCase(name);
    }
}
