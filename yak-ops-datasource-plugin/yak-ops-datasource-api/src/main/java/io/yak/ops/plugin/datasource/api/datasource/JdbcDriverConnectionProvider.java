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

    @Override
    public Connection getConnection() throws SQLException {
        try {
            Driver driver = (Driver) Class.forName(jdbcDriverClassName).getDeclaredConstructor().newInstance();
            Properties p = new Properties(properties);
            if (username != null) {
                p.setProperty("user", username);
            }
            if (password != null) {
                p.setProperty("password", password);
            }
            return driver.connect(jdbcUrl, p);
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Failed to instantiate driver: " + jdbcDriverClassName, e);
        }
    }
}
