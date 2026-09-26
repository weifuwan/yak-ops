package io.yak.ops.plugin.database.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.plugin.database.jdbc.enums.SshAuthType;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.enums.DataSourceCapability;
import io.yak.ops.plugin.datasource.api.enums.DataSourcePluginOperation;
import io.yak.ops.plugin.datasource.api.exception.DataSourcePluginException;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * JDBC 数据源插件基础实现，负责运行时插件元数据、连接参数、连通性、SSH 和 Catalog 元数据。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public abstract class AbstractJdbcDataSourcePlugin implements DataSourcePlugin {

    private static final Set<String> RESERVED_CONNECTION_PROPERTY_KEYS = Set.of(
            "host",
            "hostname",
            "pghost",
            "port",
            "pgport",
            "database",
            "databasename",
            "dbname",
            "pgdbname",
            "servicename",
            "username",
            "user",
            "password",
            "jdbcurl",
            "url",
            "driver",
            "driverclassname",
            "driverid");

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
    public List<String> connectionPropertyKeys() {
        TreeSet<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        keys.addAll(knownConnectionPropertyKeys());

        try {
            Class.forName(defaultDriverClassName());
            String jdbcUrl =
                    buildJdbcUrl("127.0.0.1", defaultPort(), propertyInfoDatabase(), JSONUtils.createObjectNode());
            Driver driver = DriverManager.getDriver(jdbcUrl);
            DriverPropertyInfo[] propertyInfo = driver.getPropertyInfo(jdbcUrl, new Properties());
            if (propertyInfo != null) {
                for (DriverPropertyInfo item : propertyInfo) {
                    if (item != null && includeConnectionPropertyKey(item.name)) {
                        keys.add(item.name.trim());
                    }
                }
            }
        } catch (Exception ignored) {
            // Driver 元数据不可用时仍返回 Provider 已知属性，不能把高级参数下拉框变成运行时连接前置条件。
        }

        keys.removeIf(key -> !includeConnectionPropertyKey(key));
        return List.copyOf(keys);
    }

    @Override
    public DataSourceConnection parseConnection(String connectionJson) {
        try {
            JsonNode root = JSONUtils.readTree(connectionJson);
            if (root == null || !root.isObject()) {
                throw parameterError("连接参数必须是 JSON 对象", null);
            }

            validateDeclaredType(root);
            String explicitUrl = JSONUtils.firstText(root, "jdbcUrl", "url");
            String host = JSONUtils.firstText(root, "host", "hostname");
            int port = intValue(root, defaultPort(), "port");
            String database = JSONUtils.firstText(root, "database", "databaseName", "serviceName");
            String schema = JSONUtils.firstText(root, "schema", "schemaName");
            String username = JSONUtils.firstText(root, "username", "user");
            String password = JSONUtils.firstText(root, "password");
            String driver = StringUtils.trimToNull(JSONUtils.firstText(root, "driverClassName", "driver"));
            if (driver == null) driver = defaultDriverClassName();
            String driverId = normalizeDriverId(root);
            SshTunnelConfig sshTunnel = parseSshTunnel(root);

            if (StringUtils.isBlank(username)) {
                throw parameterError("username 不能为空", null);
            }
            if (sshTunnel.enabled() && !StringUtils.isBlank(explicitUrl)) {
                throw parameterError("启用 SSH 隧道时请使用 host、port、database 参数，不支持自定义 JDBC 地址", null);
            }

            String jdbcUrl = explicitUrl;
            if (StringUtils.isBlank(jdbcUrl)) {
                if (StringUtils.isBlank(host)) {
                    throw parameterError("host 不能为空", null);
                }
                if (StringUtils.isBlank(database)) {
                    throw parameterError("database 不能为空", null);
                }
                jdbcUrl = buildJdbcUrl(host.trim(), port, database.trim(), root);
            } else {
                jdbcUrl = jdbcUrl.trim();
                if (!acceptsUrl(jdbcUrl)) {
                    throw parameterError("JDBC 地址与插件类型不匹配：" + jdbcUrl, null);
                }
            }

            if (StringUtils.isBlank(database)) {
                database = inferDatabase(jdbcUrl);
            }

            Map<String, String> properties = normalizeProperties(parseProperties(root.get("properties")));
            validateProperties(properties);
            schema = normalizeSchema(schema);
            ObjectNode normalized = JSONUtils.createObjectNode();
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
            putIfText(normalized, "driverId", driverId);
            normalized.put("driverClassName", driver);
            ObjectNode propertiesNode = normalized.putObject("properties");
            properties.forEach(propertiesNode::put);
            writeSshTunnel(normalized, sshTunnel);
            appendNormalizedFields(root, normalized);

            return new JdbcConnectionProperties(
                    type(),
                    StringUtils.trimToNull(host),
                    port,
                    jdbcUrl,
                    driver,
                    driverId,
                    username.trim(),
                    password,
                    StringUtils.trimToNull(database),
                    StringUtils.trimToNull(schema),
                    properties,
                    sshTunnel,
                    JSONUtils.toJson(normalized));
        } catch (DataSourcePluginException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parameterError("连接参数解析失败：" + safeMessage(exception), exception);
        }
    }

    @Override
    public void testConnection(DataSourceConnection connection, int timeoutSeconds) {
        JdbcConnectionProperties jdbcConnection = requireJdbcConnection(connection);
        int safeTimeout = Math.max(1, timeoutSeconds);
        try (Connection opened = openJdbcConnection(jdbcConnection, safeTimeout)) {
            if (opened == null || opened.isClosed() || !opened.isValid(safeTimeout)) {
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
            JsonNode normalized = JSONUtils.readTree(connection.normalizedJson());
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

    protected Set<String> knownConnectionPropertyKeys() {
        return Set.of();
    }

    protected String propertyInfoDatabase() {
        return "database";
    }

    protected boolean includeConnectionPropertyKey(String key) {
        String value = StringUtils.trimToNull(key);
        if (value == null) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return !RESERVED_CONNECTION_PROPERTY_KEYS.contains(normalized)
                && !normalized.contains(".testsuite.faultinjection.")
                && !normalized.contains(".faultinjection.");
    }

    protected Map<String, String> normalizeProperties(Map<String, String> properties) {
        return properties;
    }

    protected void validateProperties(Map<String, String> properties) {}

    protected String normalizeSchema(String schema) {
        return StringUtils.trimToNull(schema);
    }

    protected String normalizeDriverId(JsonNode connectionJson) {
        String driverId = StringUtils.trimToNull(JSONUtils.firstText(connectionJson, "driverId"));
        if (driverId != null) {
            throw parameterError("当前数据源不支持 JDBC Driver 选择：" + driverId, null);
        }
        return null;
    }

    protected final Map<String, String> canonicalizeProperties(
            Map<String, String> properties, Map<String, String> canonicalKeys) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = StringUtils.trimToNull(entry.getKey());
            if (key == null) {
                throw parameterError("JDBC 属性名不能为空", null);
            }
            String canonicalKey = canonicalKeys.getOrDefault(key.toLowerCase(Locale.ROOT), key);
            if (normalized.containsKey(canonicalKey)) {
                throw parameterError("JDBC 属性重复：" + canonicalKey, null);
            }
            normalized.put(canonicalKey, entry.getValue());
        }
        return normalized;
    }

    protected final void normalizeBooleanProperty(Map<String, String> properties, String key) {
        if (!properties.containsKey(key)) return;
        String value = StringUtils.trimToNull(properties.get(key));
        if (value == null) {
            throw parameterError("JDBC 属性 " + key + " 不能为空", null);
        }
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw parameterError("JDBC 属性 " + key + " 仅支持 true 或 false", null);
        }
        properties.put(key, value.toLowerCase(Locale.ROOT));
    }

    protected final void normalizeUpperCaseEnumProperty(
            Map<String, String> properties, String key, Set<String> allowedValues) {
        if (!properties.containsKey(key)) return;
        String value = StringUtils.trimToNull(properties.get(key));
        if (value == null) {
            throw parameterError("JDBC 属性 " + key + " 不能为空", null);
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw parameterError("JDBC 属性 " + key + " 不支持值：" + value, null);
        }
        properties.put(key, normalized);
    }

    protected final void normalizeLowerCaseEnumProperty(
            Map<String, String> properties, String key, Set<String> allowedValues) {
        if (!properties.containsKey(key)) return;
        String value = StringUtils.trimToNull(properties.get(key));
        if (value == null) {
            throw parameterError("JDBC 属性 " + key + " 不能为空", null);
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw parameterError("JDBC 属性 " + key + " 不支持值：" + value, null);
        }
        properties.put(key, normalized);
    }

    protected final void validateNonNegativeIntegerProperty(Map<String, String> properties, String key) {
        if (!properties.containsKey(key)) return;
        String value = StringUtils.trimToNull(properties.get(key));
        if (value == null) {
            throw parameterError("JDBC 属性 " + key + " 不能为空", null);
        }
        try {
            if (Integer.parseInt(value) < 0) {
                throw parameterError("JDBC 属性 " + key + " 不能小于 0", null);
            }
        } catch (NumberFormatException exception) {
            throw parameterError("JDBC 属性 " + key + " 必须是整数", exception);
        }
        properties.put(key, value);
    }

    protected final void validatePositiveIntegerProperty(Map<String, String> properties, String key) {
        if (!properties.containsKey(key)) return;
        String value = StringUtils.trimToNull(properties.get(key));
        if (value == null) {
            throw parameterError("JDBC 属性 " + key + " 不能为空", null);
        }
        try {
            if (Integer.parseInt(value) <= 0) {
                throw parameterError("JDBC 属性 " + key + " 必须大于 0", null);
            }
        } catch (NumberFormatException exception) {
            throw parameterError("JDBC 属性 " + key + " 必须是整数", exception);
        }
        properties.put(key, value);
    }

    protected final void validateNonBlankProperty(Map<String, String> properties, String key) {
        if (!properties.containsKey(key)) return;
        String value = StringUtils.trimToNull(properties.get(key));
        if (value == null) {
            throw parameterError("JDBC 属性 " + key + " 不能为空", null);
        }
        properties.put(key, value);
    }

    protected void appendNormalizedFields(JsonNode source, ObjectNode normalized) {}

    protected String inferDatabase(String jdbcUrl) {
        if (StringUtils.isBlank(jdbcUrl)) {
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
        if (!StringUtils.isBlank(connection.username())) {
            properties.setProperty("user", connection.username());
        }
        if (connection.password() != null) {
            properties.setProperty("password", connection.password());
        }
        return properties;
    }

    protected String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (StringUtils.isBlank(message)) {
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

        String host = StringUtils.trimToNull(JSONUtils.firstText(node, "host", "sshHost"));
        int port = intValue(node, 22, "port");
        String username = StringUtils.trimToNull(JSONUtils.firstText(node, "username", "user"));
        String authValue = StringUtils.trimToNull(JSONUtils.firstText(node, "authType", "authenticationType"));
        if (authValue == null) authValue = SshAuthType.PASSWORD.name();
        authValue = authValue.toUpperCase(Locale.ROOT);
        SshAuthType authType;
        try {
            authType = SshAuthType.valueOf(authValue);
        } catch (IllegalArgumentException exception) {
            throw parameterError("SSH 认证方式仅支持 PASSWORD 或 PRIVATE_KEY", exception);
        }

        String sshPassword = JSONUtils.firstText(node, "password");
        String privateKey = JSONUtils.firstText(node, "privateKey", "privateKeyContent");
        String passphrase = JSONUtils.firstText(node, "passphrase", "privateKeyPassphrase");
        boolean strictHostKeyChecking = booleanValue(node, false, "strictHostKeyChecking");
        String knownHosts = JSONUtils.firstText(node, "knownHosts", "knownHostsContent");

        if (StringUtils.isBlank(host)) {
            throw parameterError("SSH host 不能为空", null);
        }
        if (StringUtils.isBlank(username)) {
            throw parameterError("SSH username 不能为空", null);
        }
        if (authType == SshAuthType.PASSWORD && StringUtils.isBlank(sshPassword)) {
            throw parameterError("SSH password 不能为空", null);
        }
        if (authType == SshAuthType.PRIVATE_KEY && StringUtils.isBlank(privateKey)) {
            throw parameterError("SSH privateKey 不能为空", null);
        }
        if (strictHostKeyChecking && StringUtils.isBlank(knownHosts)) {
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
        String declaredType = JSONUtils.firstText(root, "dbType", "type", "pluginType");
        if (StringUtils.isBlank(declaredType)) {
            return;
        }
        if (!descriptor().matchesType(declaredType)) {
            throw parameterError("连接参数中的数据源类型与插件不匹配", null);
        }
    }

    private Map<String, String> parseProperties(JsonNode node) {
        if (node == null || node.isNull() || (node.isTextual() && StringUtils.isBlank(node.asText()))) {
            return Collections.emptyMap();
        }
        JsonNode objectNode = node;
        try {
            if (node.isTextual()) {
                objectNode = JSONUtils.readTree(node.asText());
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
        if (value == null || value.isNull() || StringUtils.isBlank(value.asText())) {
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
        if (value == null || value.isNull() || StringUtils.isBlank(value.asText())) {
            return defaultValue;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return Boolean.parseBoolean(value.asText());
    }

    private void putIfText(ObjectNode target, String key, String value) {
        if (!StringUtils.isBlank(value)) {
            target.put(key, value.trim());
        }
    }

    protected final DataSourcePluginException parameterError(String message, Throwable cause) {
        return cause == null
                ? new DataSourcePluginException(DataSourcePluginOperation.PARAMETER, message)
                : new DataSourcePluginException(DataSourcePluginOperation.PARAMETER, message, cause);
    }
}
