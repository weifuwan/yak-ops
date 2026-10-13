package io.yak.ops.business.datasource.runtime;

import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Holds one trusted Datasource binding and delegates JDBC connection ownership to its provider.
 *
 * <p>Credentials stay in memory and must never be included in diagnostics or persisted Task plans.
 */
public final class DataSourceRuntimeConnection {

    private final DataSourcePlugin plugin;
    private final DataSourceConnection connection;
    private final String datasourceType;
    private final long revision;
    private final int timeoutSeconds;
    private final String host;
    private final int port;
    private final boolean tunneled;

    public DataSourceRuntimeConnection(
            DataSourcePlugin plugin,
            DataSourceConnection connection,
            String datasourceType,
            long revision,
            int timeoutSeconds) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.datasourceType = Objects.requireNonNull(datasourceType, "datasourceType");
        if (revision < 1) {
            throw new IllegalArgumentException("Datasource revision must be initialized");
        }
        this.revision = revision;
        this.timeoutSeconds = timeoutSeconds;

        String resolvedHost = null;
        int resolvedPort = 3306;
        boolean usesTunnel = false;
        if (connection instanceof JdbcConnectionProperties jdbc) {
            resolvedHost = jdbc.host();
            resolvedPort = jdbc.port();
            usesTunnel = jdbc.sshTunnel().enabled();
        }
        if (resolvedHost == null && connection.jdbcUrl().startsWith("jdbc:mysql://")) {
            URI url = URI.create(connection.jdbcUrl().substring(5));
            resolvedHost = url.getHost();
            if (url.getPort() > 0) {
                resolvedPort = url.getPort();
            }
        }
        this.host = resolvedHost;
        this.port = resolvedPort;
        this.tunneled = usesTunnel;
    }

    /** Returns a provider that creates a fresh JDBC connection for each read or write. */
    public JdbcConnectionProvider provider() {
        return () -> {
            try {
                Connection opened = plugin.openConnection(connection, timeoutSeconds);
                if (opened == null) {
                    throw new SQLException("Datasource provider returned a null JDBC connection");
                }
                return opened;
            } catch (SQLException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new SQLException("Unable to open registered datasource connection", exception);
            }
        };
    }

    public String jdbcUrl() {
        return connection.jdbcUrl();
    }

    public String datasourceType() {
        return datasourceType;
    }

    public String username() {
        return connection.username();
    }

    public String password() {
        return connection.password();
    }

    public String database() {
        return connection.database();
    }

    public String schema() {
        return connection.schema();
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public long revision() {
        return revision;
    }

    public boolean tunneled() {
        return tunneled;
    }

    @Override
    public String toString() {
        return "DataSourceRuntimeConnection{credentials=redacted}";
    }
}
