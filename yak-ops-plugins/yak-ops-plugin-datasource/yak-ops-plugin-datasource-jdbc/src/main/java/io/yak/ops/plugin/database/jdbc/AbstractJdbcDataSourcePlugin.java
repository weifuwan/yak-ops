package io.yak.ops.plugin.database.jdbc;

import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.DATABASE;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.DB_TYPE;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.DRIVER_CLASS_NAME;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.HOST;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.JDBC_URL;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.PASSWORD;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.PROPERTIES;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.SCHEMA;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.SSH_AUTH_TYPE;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.SSH_ENABLED;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.SSH_HOST;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.SSH_KNOWN_HOSTS;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.SSH_PASSPHRASE;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.SSH_PASSWORD;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.SSH_PORT;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.SSH_PRIVATE_KEY;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.SSH_STRICT_HOST_KEY_CHECKING;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.SSH_USERNAME;
import static io.yak.ops.plugin.database.jdbc.JdbcDataSourceOptions.USERNAME;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Condition;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.Expression;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.configuration.validation.OptionValidationException;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.SshAuthType;
import io.yak.ops.connector.jdbc.database.connection.SshTunnelConfig;
import io.yak.ops.plugin.datasource.api.enums.DataSourceCapability;
import io.yak.ops.plugin.datasource.api.enums.DataSourcePluginOperation;
import io.yak.ops.plugin.datasource.api.exception.DataSourcePluginException;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 * Provides JDBC datasource plugin metadata, parameter normalization, connectivity testing and SSH-aware connections.
 */
public abstract class AbstractJdbcDataSourcePlugin implements DataSourcePlugin {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Builds a single declarative JDBC connection contract including optional SSH settings.
     *
     * <p>A pre-existing normalized connection stores both host and jdbcUrl; the presence of
     * jdbcUrl therefore skips host/database requirements without rejecting structured fields.
     */
    @Override
    public final OptionRule optionRule() {
        OptionRule structured = OptionRule.builder()
                .required(HOST, Conditions.notBlank(HOST))
                .required(DATABASE, Conditions.notBlank(DATABASE))
                .build();

        OptionRule sshEnabled = OptionRule.builder()
                .required(HOST, Conditions.notBlank(HOST))
                .required(DATABASE, Conditions.notBlank(DATABASE))
                .required(SSH_HOST, Conditions.notBlank(SSH_HOST))
                .required(SSH_USERNAME, Conditions.notBlank(SSH_USERNAME))
                .conditionalRule(
                        Expression.of(Condition.of(SSH_AUTH_TYPE, "PASSWORD")),
                        OptionRule.builder()
                                .required(SSH_PASSWORD, Conditions.notBlank(SSH_PASSWORD))
                                .build())
                .conditionalRule(
                        Expression.of(Condition.of(SSH_AUTH_TYPE, "PRIVATE_KEY")),
                        OptionRule.builder()
                                .required(SSH_PRIVATE_KEY, Conditions.notBlank(SSH_PRIVATE_KEY))
                                .build())
                .conditionalRule(
                        Expression.of(Condition.of(SSH_STRICT_HOST_KEY_CHECKING, true)),
                        OptionRule.builder()
                                .required(SSH_KNOWN_HOSTS, Conditions.notBlank(SSH_KNOWN_HOSTS))
                                .build())
                .build();

        Option<Integer> port = portOption();
        return OptionRule.builder()
                .required(USERNAME, Conditions.notBlank(USERNAME))
                .optional(JDBC_URL, Conditions.notBlank(JDBC_URL))
                .optional(HOST, DATABASE, SCHEMA, PASSWORD, DB_TYPE, DRIVER_CLASS_NAME, PROPERTIES)
                .optional(port, Conditions.greaterOrEqual(port, 1).and(Conditions.lessOrEqual(port, 65535)))
                .optional(
                        SSH_ENABLED,
                        SSH_HOST,
                        SSH_USERNAME,
                        SSH_AUTH_TYPE,
                        SSH_PASSWORD,
                        SSH_PRIVATE_KEY,
                        SSH_PASSPHRASE,
                        SSH_STRICT_HOST_KEY_CHECKING,
                        SSH_KNOWN_HOSTS)
                .optional(SSH_PORT, Conditions.greaterOrEqual(SSH_PORT, 1).and(Conditions.lessOrEqual(SSH_PORT, 65535)))
                .conditionalRule(Expression.of(Condition.of(JDBC_URL, null)), structured)
                .conditionalRule(Expression.of(Condition.of(SSH_ENABLED, true)), sshEnabled)
                .include(providerOptionRule())
                .build();
    }

    /** Returns the provider-specific additions to the shared connection rules. */
    protected OptionRule providerOptionRule() {
        return OptionRule.builder().build();
    }

    private Option<Integer> portOption() {
        return Options.key("port").intType().defaultValue(defaultPort());
    }

    /**
     * Converts recognized input names to canonical option keys before validation.
     *
     * <p>Saved connections already use the canonical names. This step preserves existing
     * supported request aliases without introducing a second parsing API.
     */
    private static ReadonlyConfig connectionOptions(ReadonlyConfig supplied) {
        Map<String, Object> fields = new LinkedHashMap<>(supplied.getSourceMap());
        // HTTP DTOs represent omitted optional fields as null. Unknown values remain errors.
        fields.values().removeIf(java.util.Objects::isNull);
        useKey(fields, "jdbcUrl", "url");
        useKey(fields, "host", "hostname");
        useKey(fields, "database", "databaseName");
        useKey(fields, "database", "serviceName");
        useKey(fields, "username", "user");
        useKey(fields, "schema", "schemaName");
        useKey(fields, "driverClassName", "driver");
        useKey(fields, "dbType", "type");
        useKey(fields, "dbType", "pluginType");
        useKey(fields, "sshTunnel", "ssh");
        Object tunnel = fields.get("sshTunnel");
        if (tunnel instanceof Map<?, ?> values) {
            Map<String, Object> ssh = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                if (key instanceof String name) {
                    ssh.put(name, value);
                }
            });
            useKey(ssh, "host", "sshHost");
            useKey(ssh, "username", "user");
            useKey(ssh, "authType", "authenticationType");
            useKey(ssh, "privateKey", "privateKeyContent");
            useKey(ssh, "passphrase", "privateKeyPassphrase");
            useKey(ssh, "knownHosts", "knownHostsContent");
            Object auth = ssh.get("authType");
            if (auth instanceof String text) {
                ssh.put("authType", text.trim().toUpperCase(Locale.ROOT));
            }
            fields.put("sshTunnel", ssh);
        }
        return ReadonlyConfig.fromMap(fields);
    }

    private static void useKey(Map<String, Object> values, String field, String alias) {
        if (!values.containsKey(field) && values.containsKey(alias)) {
            values.put(field, values.get(alias));
        }
        values.remove(alias);
    }

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
        return EnumSet.of(DataSourceCapability.CONNECTION_TEST, DataSourceCapability.SSH_TUNNEL);
    }

    protected Set<String> secretFieldKeys() {
        return Set.of("password", "privateKey", "privateKeyContent", "passphrase", "privateKeyPassphrase");
    }

    @Override
    public List<String> connectionPropertyKeys() {
        TreeSet<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        keys.addAll(knownConnectionPropertyKeys());

        try {
            String jdbcUrl =
                    buildJdbcUrl("127.0.0.1", defaultPort(), propertyInfoDatabase(), JSONUtils.createObjectNode());
            DriverPropertyInfo[] propertyInfo = connectionPropertyInfo(jdbcUrl, new Properties());
            if (propertyInfo != null) {
                for (DriverPropertyInfo item : propertyInfo) {
                    if (item != null && includeConnectionPropertyKey(item.name)) {
                        keys.add(item.name.trim());
                    }
                }
            }
        } catch (Exception ignored) {
            // If driver metadata is unavailable, keep the provider's known properties as editor suggestions without
            // making discovery a connection prerequisite.
        }

        keys.removeIf(key -> !includeConnectionPropertyKey(key));
        return List.copyOf(keys);
    }

    @Override
    public DataSourceConnection parseConnection(ReadonlyConfig configuration) {
        try {
            ReadonlyConfig input = connectionOptions(configuration);
            JsonNode root = MAPPER.valueToTree(input.getSourceMap());
            Map<String, String> properties = normalizeProperties(parseProperties(root.get("properties")));

            Map<String, Object> values = new LinkedHashMap<>(input.getSourceMap());
            values.put("properties", properties);
            ReadonlyConfig options = ReadonlyConfig.fromMap(values);
            OptionRule rule = optionRule();
            ConfigValidator.validateUnknownKeys(options, rule, type());
            ConfigValidator.of(options).validate(rule);
            validateDeclaredType(root);

            String explicitUrl = StringUtils.trimToNull(options.get(JDBC_URL));
            String host = StringUtils.trimToNull(options.get(HOST));
            int port = options.get(portOption());
            String database = StringUtils.trimToNull(options.get(DATABASE));
            String schema = StringUtils.trimToNull(options.get(SCHEMA));
            String username = options.get(USERNAME).trim();
            String password = options.get(PASSWORD);
            String driverId = normalizeDriverId(root);
            String driver = normalizeDriverClassName(root, driverId);
            SshTunnelConfig sshTunnel = parseSshTunnel(options);

            String jdbcUrl;
            if (sshTunnel.enabled()) {
                // A stored normalized tunnel configuration also contains a generated jdbcUrl.
                // Always construct the tunnel target from its authorized structured fields.
                jdbcUrl = buildJdbcUrl(host, port, database, root);
            } else if (explicitUrl == null) {
                jdbcUrl = buildJdbcUrl(host, port, database, root);
            } else {
                jdbcUrl = explicitUrl;
                if (!acceptsUrl(jdbcUrl)) {
                    throw parameterError("JDBC URL does not match the selected provider", null);
                }
            }

            if (StringUtils.isBlank(database)) {
                database = inferDatabase(jdbcUrl);
            }

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

            return new JdbcConnectionProperties(
                    type(),
                    host,
                    port,
                    jdbcUrl,
                    driver,
                    driverId,
                    username,
                    password,
                    database,
                    schema,
                    properties,
                    sshTunnel,
                    JSONUtils.toJson(normalized));
        } catch (OptionValidationException exception) {
            throw exception;
        } catch (DataSourcePluginException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parameterError("Invalid datasource connection parameters", exception);
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
    public Connection openConnection(DataSourceConnection connection, int timeoutSeconds) throws Exception {
        return openJdbcConnection(requireJdbcConnection(connection), Math.max(1, timeoutSeconds));
    }

    protected Connection openJdbcConnection(JdbcConnectionProperties connection, int timeoutSeconds) throws Exception {
        return new DefaultJdbcConnectionProvider(
                        new JdbcConnectionOptions(connectionOptions(connection, timeoutSeconds)))
                .getConnection();
    }

    @Override
    public final ReadonlyConfig connectionOptions(DataSourceConnection connection, int timeoutSeconds) {
        JdbcConnectionProperties jdbc = requireJdbcConnection(connection);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(JdbcConnectionOptions.URL.key(), jdbc.jdbcUrl());
        values.put(JdbcConnectionOptions.DRIVER_CLASS.key(), jdbc.driverClassName());
        values.put(JdbcConnectionOptions.DRIVER_ID.key(), jdbc.driverId());
        values.put(JdbcConnectionOptions.DATABASE_TYPE.key(), jdbc.type());
        values.put(JdbcConnectionOptions.USERNAME.key(), jdbc.username());
        values.put(JdbcConnectionOptions.PASSWORD.key(), jdbc.password());
        values.put(JdbcConnectionOptions.HOST.key(), jdbc.host());
        values.put(JdbcConnectionOptions.PORT.key(), jdbc.port());
        values.put(JdbcConnectionOptions.PROPERTIES.key(), jdbc.properties());
        values.put(JdbcConnectionOptions.TIMEOUT.key(), Math.max(1, timeoutSeconds));
        SshTunnelConfig ssh = jdbc.sshTunnel();
        values.put(JdbcConnectionOptions.SSH_ENABLED.key(), ssh.enabled());
        if (ssh.enabled()) {
            values.put(JdbcConnectionOptions.SSH_HOST.key(), ssh.host());
            values.put(JdbcConnectionOptions.SSH_PORT.key(), ssh.port());
            values.put(JdbcConnectionOptions.SSH_USERNAME.key(), ssh.username());
            values.put(JdbcConnectionOptions.SSH_AUTH_TYPE.key(), ssh.authType().name());
            values.put(JdbcConnectionOptions.SSH_PASSWORD.key(), ssh.password());
            values.put(JdbcConnectionOptions.SSH_PRIVATE_KEY.key(), ssh.privateKey());
            values.put(JdbcConnectionOptions.SSH_PASSPHRASE.key(), ssh.passphrase());
            values.put(JdbcConnectionOptions.SSH_STRICT_HOST_KEYS.key(), ssh.strictHostKeyChecking());
            values.put(JdbcConnectionOptions.SSH_KNOWN_HOSTS.key(), ssh.knownHosts());
            String loopbackUrl =
                    buildJdbcUrl("127.0.0.1", 0, jdbc.database(), JSONUtils.readTree(jdbc.normalizedJson()));
            values.put(
                    JdbcConnectionOptions.SSH_JDBC_URL.key(), loopbackUrl.replace("127.0.0.1:0", "127.0.0.1:{port}"));
        }
        return ReadonlyConfig.fromMap(values);
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

    protected Driver connectionPropertyDriver(String jdbcUrl) throws Exception {
        Class.forName(defaultDriverClassName());
        return DriverManager.getDriver(jdbcUrl);
    }

    protected DriverPropertyInfo[] connectionPropertyInfo(String jdbcUrl, Properties properties) throws Exception {
        return connectionPropertyDriver(jdbcUrl).getPropertyInfo(jdbcUrl, properties);
    }

    protected final String displayJdbcUrlWithQueryProperties(DataSourceConnection connection) {
        JdbcConnectionProperties jdbcConnection = requireJdbcConnection(connection);
        String jdbcUrl = jdbcConnection.jdbcUrl();
        if (StringUtils.isBlank(jdbcUrl) || jdbcConnection.properties().isEmpty()) return jdbcUrl;

        StringBuilder displayUrl = new StringBuilder(jdbcUrl);
        String separator = jdbcUrl.contains("?") ? (jdbcUrl.endsWith("?") || jdbcUrl.endsWith("&") ? "" : "&") : "?";
        for (Map.Entry<String, String> entry : jdbcConnection.properties().entrySet()) {
            if (StringUtils.isBlank(entry.getKey())) continue;
            displayUrl
                    .append(separator)
                    .append(encodeQueryComponent(entry.getKey()))
                    .append("=")
                    .append(encodeQueryComponent(entry.getValue()));
            separator = "&";
        }
        return displayUrl.toString();
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

    protected String normalizeDriverClassName(JsonNode connectionJson, String driverId) {
        String driver = StringUtils.trimToNull(JSONUtils.firstText(connectionJson, "driverClassName", "driver"));
        return driver == null ? defaultDriverClassName() : driver;
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

    protected String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (StringUtils.isBlank(message)) {
            return throwable == null ? "未知错误" : throwable.getClass().getSimpleName();
        }
        String sanitized = SensitiveUtils.mask(message);
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }

    private static String encodeQueryComponent(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private SshTunnelConfig parseSshTunnel(ReadonlyConfig options) {
        if (!options.get(SSH_ENABLED)) {
            return SshTunnelConfig.disabled();
        }
        return new SshTunnelConfig(
                true,
                options.get(SSH_HOST).trim(),
                options.get(SSH_PORT),
                options.get(SSH_USERNAME).trim(),
                SshAuthType.valueOf(options.get(SSH_AUTH_TYPE)),
                options.get(SSH_PASSWORD),
                options.get(SSH_PRIVATE_KEY),
                options.get(SSH_PASSPHRASE),
                options.get(SSH_STRICT_HOST_KEY_CHECKING),
                options.get(SSH_KNOWN_HOSTS));
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
