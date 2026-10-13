package io.yak.ops.plugin.task.datasync.resource;

import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import java.util.Objects;

/**
 * Trusted runtime connection binding with credentials excluded from diagnostics.
 *
 * <p>Each JDBC reader or writer obtains a fresh connection through the provider.
 */
public final class SyncRuntimeConnection {

    private final JdbcConnectionProvider provider;
    private final String jdbcUrl;
    private final String datasourceType;
    private final String username;
    private final String password;
    private final String host;
    private final int port;
    private final long revision;
    private final boolean tunneled;

    public SyncRuntimeConnection(
            JdbcConnectionProvider provider,
            String jdbcUrl,
            String datasourceType,
            String username,
            String password,
            String host,
            int port,
            long revision,
            boolean tunneled) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.datasourceType = Objects.requireNonNull(datasourceType, "datasourceType");
        this.username = username;
        this.password = password;
        this.host = host;
        this.port = port;
        this.revision = revision;
        this.tunneled = tunneled;
    }

    public JdbcConnectionProvider provider() {
        return provider;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String datasourceType() {
        return datasourceType;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
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
        return "SyncRuntimeConnection{credentials=redacted}";
    }
}
