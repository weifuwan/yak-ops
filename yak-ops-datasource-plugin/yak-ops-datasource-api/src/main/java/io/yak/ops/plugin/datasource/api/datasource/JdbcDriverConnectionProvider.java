package io.yak.ops.plugin.datasource.api.datasource;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

import lombok.Builder;

@Builder
public class JdbcDriverConnectionProvider implements JdbcConnectionProvider {

    private final String jdbcDriverClassName;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final Properties properties;

    /**
     * Instantiate only JDBC driver class names selected by backend datasource plugins.
     */
    public static Driver instantiateDriver(String driverClassName) throws SQLException {
        try {
            return (Driver) Class.forName(driverClassName).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Failed to instantiate driver: " + driverClassName, e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        Driver driver = instantiateDriver(jdbcDriverClassName);
        Properties p = new Properties(properties);
        if (username != null) {
            p.setProperty("user", username);
        }
        if (password != null) {
            p.setProperty("password", password);
        }
        return driver.connect(jdbcUrl, p);
    }
}
