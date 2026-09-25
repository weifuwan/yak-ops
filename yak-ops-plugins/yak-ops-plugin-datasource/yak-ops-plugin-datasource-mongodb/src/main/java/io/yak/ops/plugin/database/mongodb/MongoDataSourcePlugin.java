package io.yak.ops.plugin.database.mongodb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.ConnectionForm;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FieldType;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormField;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormRule;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormSection;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonInt32;

/** MongoDB datasource-management plugin. */
public final class MongoDataSourcePlugin implements DataSourcePlugin {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_PORT = 27017;

    private static final DataSourcePluginDescriptor DESCRIPTOR = new DataSourcePluginDescriptor(
            "MONGODB",
            "MongoDB",
            java.util.Set.of("MONGO", "MONGO_DB"),
            DataSourcePluginDescriptor.CURRENT_API_VERSION,
            EnumSet.of(
                    DataSourceCapability.CONNECTION_TEST,
                    DataSourceCapability.CATALOG_METADATA),
            connectionForm(),
            false,
            null);

    @Override
    public String type() {
        return "MONGODB";
    }

    @Override
    public DataSourcePluginDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public DataSourceConnection parseConnection(String originalJson) {
        ObjectNode input = readObject(originalJson);
        String host = defaultText(text(input, "host"), "127.0.0.1");
        int port = positivePort(input.path("port").asInt(DEFAULT_PORT));
        String hosts = text(input, "hosts");
        String database = requireText(text(input, "database"), "database");
        String username = text(input, "username");
        String password = textAllowEmpty(input, "password");
        String authSource = text(input, "authSource");
        List<String> seeds = normalizeSeeds(hosts, host, port);

        String uri = buildUri(seeds, database, username, password, authSource, true);
        String displayUrl = buildUri(seeds, database, null, null, authSource, false);
        parseConnectionString(uri);

        ObjectNode normalized = MAPPER.createObjectNode();
        normalized.put("dbType", type());
        normalized.put("host", host);
        normalized.put("port", port);
        normalized.put("database", database);
        if (hosts != null) normalized.put("hosts", hosts);
        if (username != null) normalized.put("username", username);
        if (password != null) normalized.put("password", password);
        if (authSource != null) normalized.put("authSource", authSource);

        return new MongoConnection(uri, displayUrl, database, username, password, write(normalized));
    }

    @Override
    public void testConnection(DataSourceConnection connection, int timeoutSeconds) {
        MongoConnection mongo = requireConnection(connection);
        try (MongoClient client = MongoClientFactory.create(mongo, timeoutSeconds)) {
            client.getDatabase(mongo.database()).runCommand(new BsonDocument("ping", new BsonInt32(1)));
        } catch (RuntimeException failure) {
            throw new DataSourcePluginException(Operation.CONNECTIVITY, "MongoDB 连接测试失败，请检查地址、认证信息和数据库权限", failure);
        }
    }

    @Override
    public DataSourceCatalog createCatalog(DataSourceConnection connection, int timeoutSeconds) {
        return new MongoDataSourceCatalog(requireConnection(connection), timeoutSeconds);
    }

    @Override
    public boolean acceptsUrl(String url) {
        if (url == null) return false;
        String normalized = url.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("mongodb://") || normalized.startsWith("mongodb+srv://");
    }

    private static MongoConnection requireConnection(DataSourceConnection connection) {
        if (!(connection instanceof MongoConnection mongo)) {
            throw new DataSourcePluginException(Operation.PARAMETER, "MongoDB 插件收到不兼容的数据源连接模型");
        }
        return mongo;
    }

    private static ConnectionForm connectionForm() {
        FormRule required = new FormRule(true, null, null, null, "不能为空");
        FormRule portRule = new FormRule(true, null, 1, 65535, "端口范围 1-65535");

        List<FormField> basic = List.of(
                field("host", "Host", FieldType.INPUT, "127.0.0.1", "127.0.0.1", List.of(required)),
                field("port", "Port", FieldType.NUMBER, "27017", DEFAULT_PORT, List.of(portRule)),
                field("database", "Database", FieldType.INPUT, "例如：business", null, List.of(required)),
                field("username", "Username", FieldType.INPUT, "可选", null, List.of()),
                field("password", "Password", FieldType.PASSWORD, "可选", null, List.of()));

        List<FormField> advanced = List.of(
                field(
                        "hosts",
                        "Seed Hosts",
                        FieldType.TEXTAREA,
                        "可选，例如：mongo-1:27017,mongo-2:27017；填写后覆盖 Host/Port",
                        null,
                        List.of()),
                field("authSource", "Auth Source", FieldType.INPUT, "可选，例如：admin", null, List.of()));

        return new ConnectionForm(
                List.of(
                        new FormSection("basic", "连接信息", "MongoDB 默认数据库和认证信息", false, true, basic),
                        new FormSection(
                                "advanced",
                                "高级连接",
                                "Replica Set / 集群可填写多个 seed hosts；无需手工维护字段类型",
                                true,
                                false,
                                advanced)),
                List.of());
    }

    private static FormField field(
            String key, String label, FieldType type, String placeholder, Object defaultValue, List<FormRule> rules) {
        return new FormField(key, label, type, placeholder, defaultValue, List.of(), rules, List.of(), List.of(), null);
    }

    private static String buildUri(
            List<String> seeds,
            String database,
            String username,
            String password,
            String authSource,
            boolean includeCredentials) {
        StringBuilder uri = new StringBuilder("mongodb://");
        if (includeCredentials && username != null) {
            uri.append(encode(username));
            if (password != null) uri.append(':').append(encode(password));
            uri.append('@');
        }
        uri.append(String.join(",", seeds));
        uri.append('/').append(encode(database));
        if (authSource != null) {
            uri.append("?authSource=").append(encode(authSource));
        }
        return uri.toString();
    }

    private static List<String> normalizeSeeds(String hosts, String host, int port) {
        List<String> values = new ArrayList<>();
        if (hosts != null) {
            for (String item : hosts.split(",")) {
                String seed = trim(item);
                if (seed != null) values.add(normalizeSeed(seed, port));
            }
        }
        if (values.isEmpty()) values.add(normalizeSeed(requireText(host, "host"), port));
        return List.copyOf(values);
    }

    private static String normalizeSeed(String value, int defaultPort) {
        if (value.contains("://") || value.contains("/") || value.contains("?") || value.contains("@")) {
            throw new DataSourcePluginException(Operation.PARAMETER, "MongoDB Seed Host 只允许 host 或 host:port：" + value);
        }
        return value.contains(":") ? value : value + ":" + defaultPort;
    }

    private static ConnectionString parseConnectionString(String uri) {
        try {
            return new ConnectionString(requireText(uri, "uri"));
        } catch (IllegalArgumentException failure) {
            throw new DataSourcePluginException(Operation.PARAMETER, "MongoDB 连接地址格式不正确", failure);
        }
    }

    private static ObjectNode readObject(String json) {
        if (json == null || json.trim().isEmpty()) return MAPPER.createObjectNode();
        try {
            JsonNode value = MAPPER.readTree(json);
            if (value != null && value.isObject()) return (ObjectNode) value;
            throw new DataSourcePluginException(Operation.PARAMETER, "MongoDB 连接参数必须是 JSON 对象");
        } catch (JsonProcessingException failure) {
            throw new DataSourcePluginException(Operation.PARAMETER, "MongoDB 连接参数不是有效 JSON", failure);
        }
    }

    private static String write(JsonNode value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("序列化 MongoDB 连接参数失败", failure);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) return null;
        return trim(value.asText());
    }

    private static String textAllowEmpty(JsonNode node, String field) {
        if (node == null || !node.isObject()) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) return null;
        return value.asText();
    }

    private static int positivePort(int port) {
        if (port <= 0 || port > 65535) {
            throw new DataSourcePluginException(Operation.PARAMETER, "MongoDB port 必须在 1-65535 范围内");
        }
        return port;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String defaultText(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static String requireText(String value, String field) {
        String normalized = trim(value);
        if (normalized == null) {
            throw new DataSourcePluginException(Operation.PARAMETER, "MongoDB " + field + " 不能为空");
        }
        return normalized;
    }

    private static String trim(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
