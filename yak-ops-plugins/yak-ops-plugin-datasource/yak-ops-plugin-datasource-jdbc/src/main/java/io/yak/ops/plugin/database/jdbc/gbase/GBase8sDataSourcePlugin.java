package io.yak.ops.plugin.database.jdbc.gbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.GenericJdbcCatalog;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormField;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** GBase 8s datasource plugin aligned with Link-Up's database + owner JDBC semantics. */
public final class GBase8sDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PREFIX = "jdbc:gbasedbt-sqli://";
    private static final String SERVER_PROPERTY = "GBASEDBTSERVER";
    private static final String DELIMIDENT_PROPERTY = "DELIMIDENT";

    @Override
    public String type() {
        return "GBASE8S";
    }

    @Override
    protected String displayName() {
        return "GBase 8s";
    }

    @Override
    protected java.util.Set<String> aliases() {
        return java.util.Set.of("GBASE_8S");
    }

    @Override
    protected String jdbcUrlTemplate() {
        // The shared URL linkage only understands host/port/database and cannot safely carry
        // GBASEDBTSERVER. Keep linkage disabled so structured fields reach buildJdbcUrl intact.
        return null;
    }

    @Override
    protected int defaultPort() {
        return 9088;
    }

    @Override
    protected String defaultDriverClassName() {
        return "com.gbasedbt.jdbc.Driver";
    }

    @Override
    protected void appendFormFields(List<FormField> fields) {
        // Optional at form level because a complete custom JDBC URL or properties may already carry
        // GBASEDBTSERVER. For structured host/port/database mode this field is required by buildJdbcUrl.
        fields.add(field(
                "serverName",
                SERVER_PROPERTY,
                "INPUT",
                "使用 host/port/database 自动生成 JDBC 地址时必填，例如 gbase01",
                null,
                Collections.emptyList()));
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        String serverName = serverName(connectionJson);
        if (!hasText(serverName)) {
            throw new DataSourcePluginException(
                    Operation.PARAMETER, "GBase 8s 使用 host/port/database 生成 JDBC 地址时必须配置 GBASEDBTSERVER");
        }
        if (serverName.indexOf(';') >= 0 || serverName.indexOf(':') >= 0) {
            throw new DataSourcePluginException(Operation.PARAMETER, "GBASEDBTSERVER 不能包含 ':' 或 ';'");
        }
        return PREFIX + host + ":" + port + "/" + database + ":" + SERVER_PROPERTY + "=" + serverName.trim();
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.trim().toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    @Override
    protected String inferDatabase(String jdbcUrl) {
        if (!acceptsUrl(jdbcUrl)) {
            return null;
        }
        String value = jdbcUrl.trim();
        int databaseStart = value.indexOf('/', PREFIX.length());
        if (databaseStart < 0 || databaseStart == value.length() - 1) {
            return null;
        }
        int end = value.length();
        int colon = value.indexOf(':', databaseStart + 1);
        if (colon >= 0) {
            end = colon;
        }
        int semicolon = value.indexOf(';', databaseStart + 1);
        if (semicolon >= 0 && semicolon < end) {
            end = semicolon;
        }
        String database = value.substring(databaseStart + 1, end).trim();
        return hasText(database) ? database : null;
    }

    @Override
    protected void appendNormalizedFields(JsonNode source, ObjectNode normalized) {
        normalized.put("dialect", "gbase8s");
        String explicitUrl = text(source, "jdbcUrl");
        if (!hasText(explicitUrl)) {
            explicitUrl = text(source, "url");
        }

        String serverName;
        if (hasText(explicitUrl)) {
            // With a custom URL, a standalone serverName field is not passed to DriverManager. The
            // property must therefore already be present in the URL or the JDBC properties object.
            serverName = urlProperty(explicitUrl, SERVER_PROPERTY);
            if (!hasText(serverName)) {
                serverName = jsonProperty(source, SERVER_PROPERTY);
            }
        } else {
            serverName = serverName(source);
        }

        if (!hasText(serverName)) {
            throw new DataSourcePluginException(
                    Operation.PARAMETER, "GBase 8s JDBC 地址或 properties 必须配置 GBASEDBTSERVER");
        }
        normalized.put("serverName", serverName.trim());
        if (!normalized.hasNonNull("schema")
                || !hasText(normalized.path("schema").asText())) {
            String username = normalized.path("username").asText(null);
            if (hasText(username)) {
                // Link-Up treats schema as the table owner and falls back to username when omitted.
                normalized.put("schema", username.trim());
            }
        }
    }

    @Override
    protected DataSourceCatalog createJdbcCatalog(
            JdbcConnectionProperties connection, int connectionTimeoutSeconds, int queryTimeoutSeconds) {
        String owner = hasText(connection.schema()) ? connection.schema().trim() : connection.username();
        JdbcConnectionProperties ownerAware = new JdbcConnectionProperties(
                "GBASE8S",
                connection.host(),
                connection.port(),
                connection.jdbcUrl(),
                connection.driverClassName(),
                connection.username(),
                connection.password(),
                connection.database(),
                owner,
                connection.properties(),
                connection.sshTunnel(),
                connection.normalizedJson());
        boolean delimitedIdentifiers = delimitedIdentifiersEnabled(connection.jdbcUrl(), connection.properties());

        return new GenericJdbcCatalog(ownerAware, connectionTimeoutSeconds, queryTimeoutSeconds) {
            @Override
            protected Connection openConnection() throws Exception {
                return GBase8sDataSourcePlugin.this.openJdbcConnection(ownerAware, connectionTimeoutSeconds);
            }

            @Override
            protected String quoteIdentifier(String identifier) {
                if (!hasText(identifier)) {
                    throw new IllegalArgumentException("数据库标识符不能为空");
                }
                String value = identifier.trim();
                if (delimitedIdentifiers) {
                    return "\"" + value.replace("\"", "\"\"") + "\"";
                }
                if (!simpleIdentifier(value)) {
                    throw new IllegalArgumentException(
                            "GBase 8s 默认 DELIMIDENT=n，不支持需要双引号的标识符；" + "如需大小写敏感或特殊标识符，请在 properties 中设置 DELIMIDENT=y");
                }
                return value.toLowerCase(Locale.ROOT);
            }
        };
    }

    private String serverName(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        String direct = text(root, "serverName");
        if (!hasText(direct)) {
            direct = text(root, SERVER_PROPERTY);
        }
        if (hasText(direct)) {
            return direct;
        }

        String propertyValue = jsonProperty(root, SERVER_PROPERTY);
        if (hasText(propertyValue)) {
            return propertyValue;
        }

        String jdbcUrl = text(root, "jdbcUrl");
        if (!hasText(jdbcUrl)) {
            jdbcUrl = text(root, "url");
        }
        return urlProperty(jdbcUrl, SERVER_PROPERTY);
    }

    private String jsonProperty(JsonNode root, String key) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode properties = root.get("properties");
        if (properties == null || properties.isNull()) {
            return null;
        }
        if (properties.isTextual()) {
            String raw = properties.asText();
            if (!hasText(raw)) {
                return null;
            }
            try {
                properties = OBJECT_MAPPER.readTree(raw);
            } catch (Exception ignored) {
                // The shared parser reports the canonical "properties 不是合法 JSON" error first.
                return null;
            }
        }
        if (!properties.isObject()) {
            return null;
        }
        var fields = properties.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (key.equalsIgnoreCase(entry.getKey()) && entry.getValue().isValueNode()) {
                String value = entry.getValue().asText();
                return hasText(value) ? value : null;
            }
        }
        return null;
    }

    private boolean delimitedIdentifiersEnabled(String jdbcUrl, Map<String, String> properties) {
        String value = property(properties, DELIMIDENT_PROPERTY);
        if (!hasText(value)) {
            value = urlProperty(jdbcUrl, DELIMIDENT_PROPERTY);
        }
        if (!hasText(value)) {
            return false;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "y", "yes", "true", "1" -> true;
            default -> false;
        };
    }

    private String property(Map<String, String> properties, String key) {
        if (properties == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String urlProperty(String jdbcUrl, String key) {
        if (!acceptsUrl(jdbcUrl) || !hasText(key)) {
            return null;
        }
        String value = jdbcUrl.trim();
        int databaseStart = value.indexOf('/', PREFIX.length());
        if (databaseStart < 0) {
            return null;
        }
        int propertiesStart = value.indexOf(':', databaseStart + 1);
        if (propertiesStart < 0 || propertiesStart == value.length() - 1) {
            return null;
        }
        for (String pair : value.substring(propertiesStart + 1).split(";")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            if (key.equalsIgnoreCase(pair.substring(0, equals).trim())) {
                String propertyValue = pair.substring(equals + 1).trim();
                return hasText(propertyValue) ? propertyValue : null;
            }
        }
        return null;
    }

    private String text(JsonNode root, String key) {
        JsonNode value = root.get(key);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private boolean simpleIdentifier(String value) {
        if (!hasText(value)) {
            return false;
        }
        char first = value.charAt(0);
        if (!(Character.isLetter(first) || first == '_')) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!(Character.isLetterOrDigit(current) || current == '_' || current == '$' || current == '#')) {
                return false;
            }
        }
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
