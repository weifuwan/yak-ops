package io.yak.ops.connector.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Reusable JDBC connection definition. Opening a connection always creates task-owned resources.
 *
 * <p>Connection credentials are never included in Split or Enumerator checkpoint state or
 * diagnostic strings. The caller must supply an installed JDBC driver.
 */
public final class JdbcConnectionOptions implements java.io.Serializable {

    private final String url;
    private final String username;
    private final String password;
    private final String driverClass;
    private final Map<String, String> properties;

    public JdbcConnectionOptions(
            String url, String username, String password, String driverClass, Map<String, String> properties) {
        if (url == null || !url.startsWith("jdbc:")) {
            throw new IllegalArgumentException("A JDBC URL is required");
        }
        this.url = url;
        this.username = username;
        this.password = password;
        this.driverClass = driverClass;
        this.properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
    }

    public JdbcConnectionOptions(String url, String username, String password) {
        this(url, username, password, null, Map.of());
    }

    public Connection openConnection() throws SQLException {
        if (driverClass != null && !driverClass.isBlank()) {
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException exception) {
                throw new SQLException("JDBC driver is not installed", exception);
            }
        }
        Properties connectionProperties = new Properties();
        connectionProperties.putAll(properties);
        if (username != null) {
            connectionProperties.setProperty("user", username);
        }
        if (password != null) {
            connectionProperties.setProperty("password", password);
        }
        return DriverManager.getConnection(url, connectionProperties);
    }

    public String url() {
        return url;
    }

    @Override
    public String toString() {
        return "JdbcConnectionOptions{credentials=redacted}";
    }
}
