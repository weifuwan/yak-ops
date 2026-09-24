package io.yak.ops.plugin.database.jdbc;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceConnection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** JDBC 插件解析后的不可变连接参数。 */
public final class JdbcConnectionProperties implements DataSourceConnection {

    private final DataSourceDbType dbType;
    private final String host;
    private final int port;
    private final String jdbcUrl;
    private final String driverClassName;
    private final String username;
    private final String password;
    private final String database;
    private final String schema;
    private final Map<String, String> properties;
    private final SshTunnelConfig sshTunnel;
    private final String normalizedJson;

    /** 保留原构造器，兼容现有插件或测试代码的直接构造。 */
    public JdbcConnectionProperties(
            DataSourceDbType dbType,
            String jdbcUrl,
            String driverClassName,
            String username,
            String password,
            String database,
            String schema,
            Map<String, String> properties,
            String normalizedJson) {
        this(
                dbType,
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
            DataSourceDbType dbType,
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
        this.dbType = dbType;
        this.host = host;
        this.port = port;
        this.jdbcUrl = jdbcUrl;
        this.driverClassName = driverClassName;
        this.username = username;
        this.password = password;
        this.database = database;
        this.schema = schema;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        this.sshTunnel = sshTunnel == null ? SshTunnelConfig.disabled() : sshTunnel;
        this.normalizedJson = normalizedJson;
    }

    @Override
    public DataSourceDbType dbType() {
        return dbType;
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
