package io.yak.ops.plugin.datasource.postgresql.catalog;

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
 * PostgreSQL metadata provided by the installed PostgreSQL JDBC driver.
 */
public class PostgreSQLDataSourceCatalog implements DataSourceCatalog {

    private static final String JDBC_URL = "jdbc:postgresql://127.0.0.1:5432/yak_ops";

    @Override
    public List<ConnectionProperty> getConnectionProperties() {
        try {
            DriverPropertyInfo[] properties = new org.postgresql.Driver()
                    .getPropertyInfo(JDBC_URL, new Properties());
            List<ConnectionProperty> result = new ArrayList<>();
            for (DriverPropertyInfo property : properties) {
                if (property.name == null
                        || "user".equalsIgnoreCase(property.name)
                        || "password".equalsIgnoreCase(property.name)) {
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
            throw new IllegalStateException("Failed to read PostgreSQL JDBC properties", e);
        }
    }
}
