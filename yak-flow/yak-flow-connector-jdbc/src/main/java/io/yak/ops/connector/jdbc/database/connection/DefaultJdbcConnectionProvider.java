package io.yak.ops.connector.jdbc.database.connection;

import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.DRIVER_CLASS;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.DRIVER_ID;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.PASSWORD;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.PROPERTIES;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.SSH_AUTH_TYPE;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.SSH_ENABLED;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.SSH_HOST;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.SSH_JDBC_URL;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.SSH_KNOWN_HOSTS;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.SSH_PASSPHRASE;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.SSH_PASSWORD;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.SSH_PORT;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.SSH_PRIVATE_KEY;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.SSH_STRICT_HOST_KEYS;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.SSH_USERNAME;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.TIMEOUT;
import static io.yak.ops.connector.jdbc.JdbcConnectionOptions.USERNAME;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

/** Opens independent JDBC connections and releases each SSH tunnel with its connection. */
public final class DefaultJdbcConnectionProvider implements JdbcConnectionProvider {
    private final JdbcConnectionOptions connection;

    public DefaultJdbcConnectionProvider(JdbcConnectionOptions connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public Connection getConnection() throws SQLException {
        ReadonlyConfig options = connection.configuration();
        try {
            if (!options.get(SSH_ENABLED)) {
                return connect(connection.url());
            }
            SshTunnelConfig ssh = new SshTunnelConfig(
                    true,
                    options.get(SSH_HOST),
                    options.get(SSH_PORT),
                    options.get(SSH_USERNAME),
                    SshAuthType.valueOf(options.get(SSH_AUTH_TYPE)),
                    options.get(SSH_PASSWORD),
                    options.get(SSH_PRIVATE_KEY),
                    options.get(SSH_PASSPHRASE),
                    options.get(SSH_STRICT_HOST_KEYS),
                    options.get(SSH_KNOWN_HOSTS));
            SshTunnel tunnel = SshTunnel.open(ssh, connection.host(), connection.port(), options.get(TIMEOUT));
            try {
                String url = options.get(SSH_JDBC_URL).replace("{port}", Integer.toString(tunnel.localPort()));
                return SshTunneledConnection.wrap(connect(url), tunnel);
            } catch (Throwable failure) {
                tunnel.close();
                throw failure;
            }
        } catch (SQLException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new SQLException("Unable to open JDBC connection", failure);
        }
    }

    private Connection connect(String url) throws Exception {
        ReadonlyConfig options = connection.configuration();
        Properties properties = new Properties();
        properties.putAll(options.get(PROPERTIES));
        String username = options.get(USERNAME);
        if (username != null && !username.isBlank()) {
            properties.setProperty("user", username);
        }
        if (options.get(PASSWORD) != null) {
            properties.setProperty("password", options.get(PASSWORD));
        }
        String driverId = options.get(DRIVER_ID);
        if (driverId != null) {
            MySqlDriverId driver = MySqlDriverId.parse(driverId);
            properties.putIfAbsent("connectTimeout", Integer.toString(options.get(TIMEOUT) * 1000));
            return JdbcDriverLoader.getInstance()
                    .connect(driver.runtimeId(), driver.driverDirectory(), driver.driverClassName(), url, properties);
        }
        String driver = options.get(DRIVER_CLASS);
        if (driver != null && !driver.isBlank()) {
            Class.forName(driver);
        }
        DriverManager.setLoginTimeout(options.get(TIMEOUT));
        return DriverManager.getConnection(url, properties);
    }

    @Override
    public String toString() {
        return "DefaultJdbcConnectionProvider{credentials=redacted}";
    }
}
