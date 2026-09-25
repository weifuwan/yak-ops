package io.yak.ops.plugin.database.jdbc;

import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JDBC Provider 解析并规范化后的不可变连接参数。
 *
 * <p>该对象会在连接测试、Catalog 和 SSH 隧道建立过程中复用；密码和规范化 JSON 可能包含敏感信息，不得直接写入日志。</p>
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public final class JdbcConnectionProperties implements DataSourceConnection {

    /** Provider canonical type。 */
    private final String type;

    /** 数据库主机；使用自定义 JDBC URL 时可以为空。 */
    private final String host;

    /** 数据库端口。 */
    private final int port;

    /** 规范化后的 JDBC URL。 */
    private final String jdbcUrl;

    /** JDBC Driver 类名。 */
    private final String driverClassName;

    /** 数据库登录用户名。 */
    private final String username;

    /** 数据库登录密码，不得写入日志或异常文本。 */
    private final String password;

    /** 默认数据库或服务名。 */
    private final String database;

    /** 默认 Schema。 */
    private final String schema;

    /** 传递给 JDBC Driver 的附加连接属性。 */
    private final Map<String, String> properties;

    /** 可选 SSH 隧道配置。 */
    private final SshTunnelConfig sshTunnel;

    /** 可持久化的规范化连接 JSON，可能包含敏感字段。 */
    private final String normalizedJson;

    /** 保留原构造器，兼容现有插件或测试代码的直接构造。 */
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
        this.type = type;
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
