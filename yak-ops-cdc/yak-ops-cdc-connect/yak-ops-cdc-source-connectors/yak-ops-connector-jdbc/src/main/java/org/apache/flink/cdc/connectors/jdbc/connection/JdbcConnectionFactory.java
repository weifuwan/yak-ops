package org.apache.flink.cdc.connectors.jdbc.connection;

import org.apache.flink.cdc.connectors.jdbc.config.JdbcSourceConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Creates read-only JDBC connections used by metadata discovery and source readers. */
public final class JdbcConnectionFactory {

    private JdbcConnectionFactory() {}

    public static Connection openConnection(JdbcSourceConfig config) throws SQLException {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) {
                Class.forName(config.getDriverName());
            } else {
                Class.forName(config.getDriverName(), true, loader);
            }
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + config.getDriverName(), e);
        }

        Properties properties = new Properties();
        if (!config.getUsername().isEmpty()) {
            properties.setProperty("user", config.getUsername());
        }
        if (!config.getPassword().isEmpty()) {
            properties.setProperty("password", config.getPassword());
        }
        if (config.getUrl().toLowerCase(java.util.Locale.ROOT).startsWith("jdbc:mysql:")) {
            properties.setProperty("useCursorFetch", "true");
            properties.setProperty("useServerPrepStmts", "true");
        }

        Connection connection = DriverManager.getConnection(config.getUrl(), properties);
        try {
            connection.setReadOnly(true);
        } catch (SQLException ignored) {
            // Read-only is an optimization hint; some drivers do not implement it.
        }
        return connection;
    }
}
