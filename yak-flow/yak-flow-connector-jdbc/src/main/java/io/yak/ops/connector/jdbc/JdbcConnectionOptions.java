package io.yak.ops.connector.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Holds an immutable JDBC connection definition for task-local DriverManager connections.
 *
 * <p>Every {@link #openConnection()} call returns a fresh caller-owned connection. Optional
 * driver properties are copied at construction; credentials are deliberately redacted from
 * diagnostics and must not be serialized into split/checkpoint state. Drivers are supplied
 * by the deployment or driver-isolation runtime.
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

    /**
     * Opens a fresh JDBC connection using the configured driver and connection properties.
     *
     * <p>Username/password override corresponding JDBC property keys when present.
     * The caller owns transaction configuration, cancellation and connection cleanup.
     *
     * @return independent, caller-owned JDBC connection
     * @throws SQLException if the driver cannot load or opening the connection fails
     */
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
