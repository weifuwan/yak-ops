package io.yak.ops.plugin.database.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.util.JsonUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.enums.DataSourceCapability;
import io.yak.ops.plugin.datasource.api.enums.DataSourcePluginOperation;
import io.yak.ops.plugin.datasource.api.exception.DataSourcePluginException;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * JDBC 数据源插件基础实现，负责运行时插件元数据、连接参数、连通性、SSH 和 Catalog 元数据。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public abstract class AbstractJdbcDataSourcePlugin implements DataSourcePlugin {

    @Override
    public DataSourcePluginDescriptor descriptor() {
        return new DataSourcePluginDescriptor(
                type(), aliases(), DataSourcePluginDescriptor.CURRENT_API_VERSION, capabilities(), secretFieldKeys());
    }

    protected Set<String> aliases() {
        return Set.of();
    }

    protected Set<DataSourceCapability> capabilities() {
        return EnumSet.of(
                DataSourceCapability.CONNECTION_TEST,
                DataSourceCapability.CATALOG_METADATA,
                DataSourceCapability.SSH_TUNNEL);
    }

    protected Set<String> secretFieldKeys() {
        return Set.of("password", "privateKey", "privateKeyContent", "passphrase", "privateKeyPassphrase");
    }

    @Override
    public DataSourceConnection parseConnection(String connectionJson) {
        try {
            JsonNode root = JsonUtils.readTree(connectionJson);
            if (root == null || !root.isObject()) {
                throw parameterError("连接参数必须是 JSON 对象", null);
            }

            validateDeclaredType(root);
            String explicitUrl = firstText(root, "jdbcUrl", "url");
            String host = firstText(root, "host", "hostname");
            int port = intValue(root, defaultPort(), "port");
            String database = firstText(root, "database", "databaseName", "serviceName");
            String schema = firstText(root, "schema", "schemaName");
            String username = firstText(root, "username", "user");
            String password = firstText(root, "password");
            String driver = defaultIfBlank(firstText(root, "driverClassName", "driver"), defaultDriverClassName());
            SshTunnelConfig sshTunnel = parseSshTunnel(root);

            if (isBlank(username)) {
                throw parameterError("username 不能为空", null);
            }
            if (sshTunnel.enabled() && !isBlank(explicitUrl)) {
                throw parameterError("启用 SSH 隧道时请使用 host、port、database 参数，不支持自定义 JDBC 地址", null);
            }

            String jdbcUrl = explicitUrl;
            if (isBlank(jdbcUrl)) {
                if (isBlank(host)) {
                    throw parameterError("host 不能为空", null);
                }
                if (isBlank(database)) {
                    throw parameterError("database 不能为空", null);
                }
                jdbcUrl = buildJdbcUrl(host.trim(), port, database.trim(), root);
            } else {
                jdbcUrl = jdbcUrl.trim();
                if (!acceptsUrl(jdbcUrl)) {
                    throw parameterError("JDBC 地址与插件类型不匹配：" + jdbcUrl, null);
                }
            }

            if (isBlank(database)) {
                database = inferDatabase(jdbcUrl);
            }

            Map<String, String> properties = parseProperties(root.get("properties"));
            ObjectNode normalized = JsonUtils.createObjectNode();
            normalized.put("dbType", type());
            putIfText(normalized, "host", host);
            normalized.put("port", port);
            putIfText(normalized, "database", database);
            putIfText(normalized, "schema", schema);
            putIfText(normalized, "username", username);
            if (password != null) {
                normalized.put("password", password);
            }
            normalized.put("jdbcUrl", jdbcUrl);
            normalized.put("driverClassName", driver);
            ObjectNode propertiesNode = normalized.putObject("properties");
            properties.forEach(propertiesNode::put);
            writeSshTunnel(normalized, sshTunnel);
            appendNormalizedFields(root, normalized);

            return new JdbcConnectionProperties(
                    type(),
                    trimToNull(host),
                    port,
                    jdbcUrl,
                    driver,
                    username.trim(),
                    password,
                    trimToNull(database),
                    trimToNull(schema),
                    properties,
                    sshTunnel,
                    JsonUtils.toJson(normalized));
        } catch (DataSourcePluginException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parameterError("连接参数解析失败：" + safeMessage(exception), exception);
        }
    }

    @Override
    public void testConnection(DataSourceConnection connection, int timeoutSeconds) {
        JdbcConnectionProperties jdbcConnection = requireJdbcConnection(connection);
        try (Connection opened = openJdbcConnection(jdbcConnection, timeoutSeconds)) {
            if (opened == null || opened.isClosed()) {
                throw new DataSourcePluginException(DataSourcePluginOperation.CONNECTIVITY, "数据库连接不可用");
            }
        } catch (DataSourcePluginException exception) {
            throw exception;
        } catch (ClassNotFoundException exception) {
            throw new DataSourcePluginException(
                    DataSourcePluginOperation.CONNECTIVITY, "数据库驱动未安装：" + jdbcConnection.driverClassName(), exception);
        } catch (Exception exception) {
            throw new DataSourcePluginException(
                    DataSourcePluginOperation.CONNECTIVITY, safeMessage(exception), exception);
        }
    }

    @Override
    public DataSourceCatalog createCatalog(DataSourceConnection connection, int timeoutSeconds) {
        int safeTimeout = Math.max(1, timeoutSeconds);
        return createJdbcCatalog(requireJdbcConnection(connection), safeTimeout, safeTimeout);
    }

    protected DataSourceCatalog createJdbcCatalog(JdbcConnectionProperties connection, int timeoutSeconds) {
        return createJdbcCatalog(connection, timeoutSeconds, timeoutSeconds);
    }

    protected DataSourceCatalog createJdbcCatalog(
            JdbcConnectionProperties connection, int connectionTimeoutSeconds, int queryTimeoutSeconds) {
        return new GenericJdbcCatalog(connection, connectionTimeoutSeconds, queryTimeoutSeconds) {
            @Override
            protected Connection openConnection() throws Exception {
                return openJdbcConnection(connection, connectionTimeoutSeconds);
            }
        };
    }

    protected Connection openJdbcConnection(JdbcConnectionProperties connection, int timeoutSeconds) throws Exception {
        Class.forName(connection.driverClassName());
        int safeTimeout = Math.max(1, timeoutSeconds);
        DriverManager.setLoginTimeout(safeTimeout);

        SshTunnelConfig sshTunnel = connection.sshTunnel();
        if (!sshTunnel.enabled()) {
            return DriverManager.getConnection(connection.jdbcUrl(), connectionProperties(connection));
        }

        SshTunnel tunnel = SshTunnel.open(sshTunnel, connection.host(), connection.port(), safeTimeout);
        try {
            JsonNode normalized = JsonUtils.readTree(connection.normalizedJson());
            String tunneledJdbcUrl = buildJdbcUrl("127.0.0.1", tunnel.localPort(), connection.database(), normalized);
            Connection opened = DriverManager.getConnection(tunneledJdbcUrl, connectionProperties(connection));
            return SshTunneledConnection.wrap(opened, tunnel);
        } catch (Exception exception) {
            tunnel.close();
            throw exception;
        }
    }

    protected abstract int defaultPort();

    protected abstract String defaultDriverClassName();

    protected abstract String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson);

    protected void appendNormalizedFields(JsonNode source, ObjectNode normalized) {}

    protected String inferDatabase(String jdbcUrl) {
        if (isBlank(jdbcUrl)) {
            return null;
        }
        String value = jdbcUrl;
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int slashIndex = value.lastIndexOf('/');
        return slashIndex >= 0 && slashIndex < value.length() - 1 ? value.substring(slashIndex + 1) : null;
    }

    protected JdbcConnectionProperties requireJdbcConnection(DataSourceConnection connection) {
        if (!(connection instanceof JdbcConnectionProperties jdbcConnection)) {
            throw parameterError("连接参数与插件类型不匹配", null);
        }
        if (!type().equals(connection.type())) {
            throw parameterError("连接参数与插件类型不匹配", null);
        }
        return jdbcConnection;
    }

    protected Properties connectionProperties(JdbcConnectionProperties connection) {
        Properties properties = new Properties();
        properties.putAll(connection.properties());
        if (!isBlank(connection.username())) {
            properties.setProperty("user", connection.username());
        }
        if (connection.password() != null) {
            properties.setProperty("password", connection.password());
        }
        return properties;
    }

    protected String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (isBlank(message)) {
            return throwable == null ? "未知错误" : throwable.getClass().getSimpleName();
        }
        String sanitized = SensitiveUtils.mask(message);
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }

    private SshTunnelConfig parseSshTunnel(JsonNode root) {
        JsonNode node = root.get("sshTunnel");
        if (node == null || node.isNull()) {
            node = root.get("ssh");
        }
        if (node == null || node.isNull()) {
            return SshTunnelConfig.disabled();
        }
        if (!node.isObject()) {
            throw parameterError("sshTunnel 必须是 JSON 对象", null);
        }

        boolean enabled = booleanValue(node, false, "enabled");
        if (!enabled) {
            return SshTunnelConfig.disabled();
        }

        String host = trimToNull(firstText(node, "host", "sshHost"));
        int port = intValue(node, 22, "port");
        String username = trimToNull(firstText(node, "username", "user"));
        String authValue = defaultIfBlank(firstText(node, "authType", "authenticationType"), "PASSWORD")
                .toUpperCase(Locale.ROOT);
        SshTunnelConfig.AuthType authType;
        try {
            authType = SshTunnelConfig.AuthType.valueOf(authValue);
        } catch (IllegalArgumentException exception) {
            throw parameterError("SSH 认证方式仅支持 PASSWORD 或 PRIVATE_KEY", exception);
        }

        String sshPassword = firstText(node, "password");
        String privateKey = firstText(node, "privateKey", "privateKeyContent");
        String passphrase = firstText(node, "passphrase", "privateKeyPassphrase");
        boolean strictHostKeyChecking = booleanValue(node, false, "strictHostKeyChecking");
        String knownHosts = firstText(node, "knownHosts", "knownHostsContent");

        if (isBlank(host)) {
            throw parameterError("SSH host 不能为空", null);
        }
        if (isBlank(username)) {
            throw parameterError("SSH username 不能为空", null);
        }
        if (authType == SshTunnelConfig.AuthType.PASSWORD && isBlank(sshPassword)) {
            throw parameterError("SSH password 不能为空", null);
        }
        if (authType == SshTunnelConfig.AuthType.PRIVATE_KEY && isBlank(privateKey)) {
            throw parameterError("SSH privateKey 不能为空", null);
        }
        if (strictHostKeyChecking && isBlank(knownHosts)) {
            throw parameterError("开启 SSH 严格主机校验后 knownHosts 不能为空", null);
        }

        return new SshTunnelConfig(
                true,
                host,
                port,
                username,
                authType,
                sshPassword,
                privateKey,
                passphrase,
                strictHostKeyChecking,
                knownHosts);
    }

    private void writeSshTunnel(ObjectNode normalized, SshTunnelConfig config) {
        ObjectNode node = normalized.putObject("sshTunnel");
        node.put("enabled", config.enabled());
        node.put("port", config.port());
        node.put("authType", config.authType().name());
        node.put("strictHostKeyChecking", config.strictHostKeyChecking());
        if (!config.enabled()) return;

        putIfText(node, "host", config.host());
        putIfText(node, "username", config.username());
        if (config.password() != null) {
            node.put("password", config.password());
        }
        if (config.privateKey() != null) {
            node.put("privateKey", config.privateKey());
        }
        if (config.passphrase() != null) {
            node.put("passphrase", config.passphrase());
        }
        putIfText(node, "knownHosts", config.knownHosts());
    }

    private void validateDeclaredType(JsonNode root) {
        String declaredType = firstText(root, "dbType", "type", "pluginType");
        if (isBlank(declaredType)) {
            return;
        }
        if (!descriptor().matchesType(declaredType)) {
            throw parameterError("连接参数中的数据源类型与插件不匹配", null);
        }
    }

    private Map<String, String> parseProperties(JsonNode node) {
        if (node == null || node.isNull() || (node.isTextual() && isBlank(node.asText()))) {
            return Collections.emptyMap();
        }
        JsonNode objectNode = node;
        try {
            if (node.isTextual()) {
                objectNode = JsonUtils.readTree(node.asText());
            }
            if (objectNode == null || !objectNode.isObject()) {
                throw parameterError("properties 必须是 JSON 对象", null);
            }
            Map<String, String> values = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue() != null && !field.getValue().isNull()) {
                    values.put(field.getKey(), field.getValue().asText());
                }
            }
            return values;
        } catch (DataSourcePluginException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parameterError("properties 不是合法 JSON", exception);
        }
    }

    private int intValue(JsonNode root, int defaultValue, String key) {
        JsonNode value = root.get(key);
        if (value == null || value.isNull() || isBlank(value.asText())) {
            return defaultValue;
        }
        int port = value.asInt(-1);
        if (port < 1 || port > 65535) {
            throw parameterError(key + " 必须在 1 到 65535 之间", null);
        }
        return port;
    }

    private boolean booleanValue(JsonNode root, boolean defaultValue, String key) {
        JsonNode value = root.get(key);
        if (value == null || value.isNull() || isBlank(value.asText())) {
            return defaultValue;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return Boolean.parseBoolean(value.asText());
    }

    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private void putIfText(ObjectNode target, String key, String value) {
        if (!isBlank(value)) {
            target.put(key, value.trim());
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private DataSourcePluginException parameterError(String message, Throwable cause) {
        return cause == null
                ? new DataSourcePluginException(DataSourcePluginOperation.PARAMETER, message)
                : new DataSourcePluginException(DataSourcePluginOperation.PARAMETER, message, cause);
    }
}
