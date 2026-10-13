package io.yak.ops.plugin.database.jdbc;

import io.yak.ops.connector.jdbc.database.connection.SshTunnelConfig;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds immutable, provider-normalized JDBC connection parameters.
 *
 * <p>Shared by connection tests, Catalog access and SSH tunneling. Passwords and normalized JSON may contain secrets and must not be logged.
 */
public final class JdbcConnectionProperties implements DataSourceConnection {

    /** Provider canonical type。 */
    private final String type;

    /** Database host; may be absent when using a custom JDBC URL. */
    private final String host;

    /** Database port. */
    private final int port;

    /** Normalized JDBC URL. */
    private final String jdbcUrl;

    /** JDBC driver class name. */
    private final String driverClassName;

    /** Provider-normalized JDBC driver selection identifier. */
    private final String driverId;

    /** Database authentication username. */
    private final String username;

    /** Database password; must not be logged or included in exception messages. */
    private final String password;

    /** Default database or service name. */
    private final String database;

    /** Default schema. */
    private final String schema;

    /** Additional properties passed to the JDBC driver. */
    private final Map<String, String> properties;

    /** Optional SSH tunnel configuration. */
    private final SshTunnelConfig sshTunnel;

    /** Persistable normalized connection JSON; may contain credentials. */
    private final String normalizedJson;

    /** Creates immutable connection properties from the JDBC URL and supplied credentials. */
    public JdbcConnectionProperties(
            String type,
            String jdbcUrl,
            String driverClassName,
            String username,
            String password,
            String database,
            String schema,
            Map<String, String> properties,
            String normalizedJson) {
        this(
                type,
                null,
                0,
                jdbcUrl,
                driverClassName,
                username,
                password,
                database,
                schema,
                properties,
                SshTunnelConfig.disabled(),
                normalizedJson);
    }

    public JdbcConnectionProperties(
            String type,
            String host,
            int port,
            String jdbcUrl,
            String driverClassName,
            String username,
            String password,
            String database,
            String schema,
            Map<String, String> properties,
            SshTunnelConfig sshTunnel,
            String normalizedJson) {
        this(
                type,
                host,
                port,
                jdbcUrl,
                driverClassName,
                null,
                username,
                password,
                database,
                schema,
                properties,
                sshTunnel,
                normalizedJson);
    }

    public JdbcConnectionProperties(
            String type,
            String host,
            int port,
            String jdbcUrl,
            String driverClassName,
            String driverId,
            String username,
            String password,
            String database,
            String schema,
            Map<String, String> properties,
            SshTunnelConfig sshTunnel,
            String normalizedJson) {
        this.type = type;
        this.host = host;
        this.port = port;
        this.jdbcUrl = jdbcUrl;
        this.driverClassName = driverClassName;
        this.driverId = driverId;
        this.username = username;
        this.password = password;
        this.database = database;
        this.schema = schema;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        this.sshTunnel = sshTunnel == null ? SshTunnelConfig.disabled() : sshTunnel;
        this.normalizedJson = normalizedJson;
    }

    @Override
    public String type() {
        return type;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    @Override
    public String jdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public String driverClassName() {
        return driverClassName;
    }

    public String driverId() {
        return driverId;
    }

    @Override
    public String username() {
        return username;
    }

    @Override
    public String password() {
        return password;
    }

    @Override
    public String database() {
        return database;
    }

    @Override
    public String schema() {
        return schema;
    }

    @Override
    public Map<String, String> properties() {
        return properties;
    }

    public SshTunnelConfig sshTunnel() {
        return sshTunnel;
    }

    @Override
    public String normalizedJson() {
        return normalizedJson;
    }
}
