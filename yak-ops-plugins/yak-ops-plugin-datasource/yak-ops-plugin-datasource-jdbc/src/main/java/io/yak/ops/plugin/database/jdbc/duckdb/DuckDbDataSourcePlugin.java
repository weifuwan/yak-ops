package io.yak.ops.plugin.database.jdbc.duckdb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.plugin.database.jdbc.AbstractJdbcDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.database.jdbc.SshTunnelConfig;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.ConnectionForm;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FieldType;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormField;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormRule;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormSection;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * File-backed DuckDB datasource plugin.
 *
 * <p>Yak Ops control plane and Link-Up Worker are independent processes, so in-memory DuckDB URLs
 * cannot represent one shared datasource. This plugin intentionally accepts persistent file-backed
 * URLs only.</p>
 */
public final class DuckDbDataSourcePlugin extends AbstractJdbcDataSourcePlugin {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PREFIX = "jdbc:duckdb:";
    private static final String DRIVER = "org.duckdb.DuckDBDriver";

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.DUCKDB;
    }

    @Override
    public DataSourcePluginDescriptor descriptor() {
        FormField databasePath = field(
                "databasePath",
                "数据库文件",
                FieldType.INPUT,
                "例如 /data/warehouse.duckdb",
                null,
                List.of(new FormRule(true, null, null, null, "请输入 DuckDB 数据库文件路径")));
        FormField schema = field("schema", "Schema", FieldType.INPUT, "默认 main", "main", Collections.emptyList());
        FormField jdbcUrl = field(
                "jdbcUrl",
                "JDBC 地址",
                FieldType.INPUT,
                "可选；例如 jdbc:duckdb:/data/warehouse.duckdb",
                null,
                Collections.emptyList());
        FormField driver = field(
                "driverClassName",
                "驱动类",
                FieldType.INPUT,
                null,
                DRIVER,
                List.of(new FormRule(true, null, null, null, "请输入 DuckDB JDBC 驱动类")));
        FormField properties = field(
                "properties",
                "扩展属性",
                FieldType.TEXTAREA,
                "可选；JSON 对象，例如 {\"access_mode\":\"READ_ONLY\"}",
                null,
                Collections.emptyList());

        return new DataSourcePluginDescriptor(
                dbType(),
                dbType().getDisplayName(),
                DataSourcePluginDescriptor.CURRENT_API_VERSION,
                capabilities(),
                new ConnectionForm(
                        List.of(
                                new FormSection(
                                        "connection",
                                        "连接参数",
                                        "DuckDB 以数据库文件作为连接目标；该文件必须同时对 Yak Ops 与 Link-Up Worker 可见。",
                                        false,
                                        true,
                                        List.of(databasePath, schema, jdbcUrl)),
                                new FormSection("driver", "驱动配置", "", true, true, List.of(driver)),
                                new FormSection("advanced", "高级配置", "", true, false, List.of(properties))),
                        List.of(databasePath, schema, jdbcUrl, driver, properties)),
                false,
                null);
    }

    @Override
    protected Set<DataSourceCapability> capabilities() {
        return EnumSet.of(
                DataSourceCapability.CONNECTION_TEST,
                DataSourceCapability.CATALOG_METADATA);
    }

    @Override
    public DataSourceConnection parseConnection(String connectionJson) {
        ObjectNode root = parseObject(connectionJson);
        validateDeclaredType(root);

        String explicitUrl = text(root, "jdbcUrl", text(root, "url", null));
        String databasePath = text(root, "databasePath", text(root, "path", text(root, "database", null)));
        String jdbcUrl = explicitUrl;
        if (isBlank(jdbcUrl)) {
            if (isBlank(databasePath)) {
                throw parameter("请输入 DuckDB 数据库文件路径或 JDBC 地址");
            }
            jdbcUrl = databasePath.startsWith(PREFIX) ? databasePath : PREFIX + databasePath;
        }
        jdbcUrl = jdbcUrl.trim();
        validateFileBackedUrl(jdbcUrl);

        Map<String, String> properties = parseProperties(root.get("properties"));
        String instanceCache = propertyIgnoreCase(properties, "jdbc_instance_cache");
        if ("false".equalsIgnoreCase(instanceCache)) {
            throw parameter("DuckDB 不允许 jdbc_instance_cache=false；Catalog 与离线执行需要稳定共享数据库实例/文件");
        }

        String driver = text(root, "driverClassName", text(root, "driver", DRIVER));
        String schema = text(root, "schema", "main");
        String databaseName = databaseName(jdbcUrl);

        ObjectNode normalized = MAPPER.createObjectNode();
        normalized.put("dbType", dbType().name());
        normalized.put("databasePath", databaseSpec(jdbcUrl));
        normalized.put("database", databaseName);
        normalized.put("schema", schema);
        normalized.put("jdbcUrl", jdbcUrl);
        normalized.put("driverClassName", driver);
        ObjectNode propertiesNode = normalized.putObject("properties");
        properties.forEach(propertiesNode::put);

        return new JdbcConnectionProperties(
                dbType(),
                null,
                0,
                jdbcUrl,
                driver,
                null,
                null,
                databaseName,
                schema,
                properties,
                SshTunnelConfig.disabled(),
                write(normalized));
    }

    @Override
    protected int defaultPort() {
        return 0;
    }

    @Override
    protected String defaultDriverClassName() {
        return DRIVER;
    }

    @Override
    protected String buildJdbcUrl(String host, int port, String database, JsonNode connectionJson) {
        return database != null && database.startsWith(PREFIX) ? database : PREFIX + database;
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.trim().toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    private ObjectNode parseObject(String json) {
        if (isBlank(json)) throw parameter("DuckDB 连接参数不能为空");
        try {
            JsonNode value = MAPPER.readTree(json);
            if (value == null || !value.isObject()) throw parameter("DuckDB 连接参数必须是 JSON 对象");
            return (ObjectNode) value;
        } catch (JsonProcessingException exception) {
            throw new DataSourcePluginException(Operation.PARAMETER, "DuckDB 连接参数不是有效 JSON", exception);
        }
    }

    private void validateDeclaredType(JsonNode root) {
        String value = text(root, "dbType", text(root, "type", text(root, "pluginType", null)));
        if (isBlank(value)) return;
        try {
            if (DataSourceDbType.parse(value) != dbType()) {
                throw parameter("连接参数中的数据源类型与 DuckDB 插件不匹配");
            }
        } catch (IllegalArgumentException exception) {
            throw new DataSourcePluginException(Operation.PARAMETER, exception.getMessage(), exception);
        }
    }

    private void validateFileBackedUrl(String jdbcUrl) {
        if (!acceptsUrl(jdbcUrl)) throw parameter("DuckDB JDBC 地址必须以 jdbc:duckdb: 开头");
        String spec = databaseSpec(jdbcUrl);
        if (isBlank(spec)
                || "memory:".equalsIgnoreCase(spec)
                || spec.toLowerCase(Locale.ROOT).startsWith("memory:")) {
            throw parameter("当前仅支持文件型 DuckDB；内存数据库无法在 Yak Ops 与独立 Link-Up Worker 之间共享");
        }
        String lower = spec.toLowerCase(Locale.ROOT);
        if (lower.startsWith("ducklake:") || lower.startsWith("md:")) {
            throw parameter("当前阶段仅支持本地/共享文件型 DuckDB，不开放 DuckLake 或 MotherDuck URL");
        }
    }

    private String databaseSpec(String jdbcUrl) {
        String value = jdbcUrl.trim().substring(PREFIX.length());
        int option = value.indexOf(';');
        return (option >= 0 ? value.substring(0, option) : value).trim();
    }

    private String databaseName(String jdbcUrl) {
        String value = databaseSpec(jdbcUrl).replace('\\', '/');
        int slash = value.lastIndexOf('/');
        String file = slash >= 0 ? value.substring(slash + 1) : value;
        int dot = file.lastIndexOf('.');
        if (dot > 0) file = file.substring(0, dot);
        return file.isBlank() ? "duckdb" : file;
    }

    private Map<String, String> parseProperties(JsonNode node) {
        if (node == null || node.isNull() || (node.isTextual() && isBlank(node.asText()))) {
            return Collections.emptyMap();
        }
        JsonNode value = node;
        try {
            if (node.isTextual()) value = MAPPER.readTree(node.asText());
            if (value == null || !value.isObject()) throw parameter("properties 必须是 JSON 对象");
            Map<String, String> result = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (entry.getValue() != null && !entry.getValue().isNull()) {
                    result.put(entry.getKey(), entry.getValue().asText());
                }
            }
            return result;
        } catch (DataSourcePluginException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DataSourcePluginException(Operation.PARAMETER, "properties 不是合法 JSON", exception);
        }
    }

    private String propertyIgnoreCase(Map<String, String> properties, String key) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey().trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) return fallback;
        String text = value.asText();
        return isBlank(text) ? fallback : text.trim();
    }

    private String write(JsonNode value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DataSourcePluginException(Operation.PARAMETER, "序列化 DuckDB 连接参数失败", exception);
        }
    }

    private FormField field(
            String key, String label, FieldType type, String placeholder, Object defaultValue, List<FormRule> rules) {
        return new FormField(key, label, type, placeholder, defaultValue, List.of(), rules, List.of(), List.of(), null);
    }

    private DataSourcePluginException parameter(String message) {
        return new DataSourcePluginException(Operation.PARAMETER, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
