package io.yak.ops.plugin.database.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.ConnectionForm;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FieldType;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormField;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormOption;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormRule;
import io.yak.ops.spi.datasource.DataSourcePluginDescriptor.FormSection;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared SDK-free control-plane implementation for Elasticsearch 7 and Elasticsearch 8. */
abstract class AbstractElasticsearchDataSourcePlugin implements DataSourcePlugin {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_PORT = 9200;

    protected abstract int expectedMajorVersion();

    protected String displayName() {
        return type();
    }

    protected Set<String> aliases() {
        return Set.of();
    }

    @Override
    public DataSourcePluginDescriptor descriptor() {
        List<FormField> connection = List.of(
                field(
                        "scheme",
                        "协议",
                        FieldType.SELECT,
                        null,
                        "http",
                        List.of(new FormOption("HTTP", "http"), new FormOption("HTTPS", "https")),
                        required("请选择连接协议")),
                field(
                        "host",
                        "主机地址",
                        FieldType.INPUT,
                        "例如 127.0.0.1",
                        "127.0.0.1",
                        List.of(),
                        required("请输入 Elasticsearch 主机地址")),
                field(
                        "port",
                        "端口",
                        FieldType.NUMBER,
                        "默认 9200",
                        DEFAULT_PORT,
                        List.of(),
                        List.of(new FormRule(true, null, 1, 65535, "端口必须在 1 到 65535 之间"))),
                field("username", "用户名", FieldType.INPUT, "可选；启用 Basic Auth 时填写", null, List.of(), List.of()),
                field("password", "密码", FieldType.PASSWORD, "可选；启用 Basic Auth 时填写", null, List.of(), List.of()));

        FormField hosts = field(
                "hosts",
                "节点地址",
                FieldType.TEXTAREA,
                "可选；多个地址用逗号分隔，例如 http://es-1:9200,http://es-2:9200",
                null,
                List.of(),
                List.of());

        return new DataSourcePluginDescriptor(
                type(),
                displayName(),
                aliases(),
                DataSourcePluginDescriptor.CURRENT_API_VERSION,
                EnumSet.of(DataSourceCapability.CONNECTION_TEST, DataSourceCapability.CATALOG_METADATA),
                new ConnectionForm(
                        List.of(
                                new FormSection("connection", "连接参数", "", false, true, connection),
                                new FormSection(
                                        "advanced",
                                        "高级配置",
                                        "节点列表仅用于连接容错；Yak Ops Catalog 使用首个可配置节点，实际离线执行由 Link-Up Connector 管理。",
                                        true,
                                        false,
                                        List.of(hosts))),
                        concat(connection, hosts)),
                false,
                null);
    }

    @Override
    public DataSourceConnection parseConnection(String connectionJson) {
        ObjectNode source = parseObject(connectionJson);
        String scheme = text(source, "scheme", "http").toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw parameter("Elasticsearch scheme 仅支持 http 或 https");
        }

        LinkedHashSet<String> endpoints = new LinkedHashSet<>();
        appendHosts(endpoints, source.get("hosts"), scheme);
        appendTextHosts(endpoints, text(source, "url", null), scheme);
        appendTextHosts(endpoints, text(source, "endpoint", null), scheme);

        String host = text(source, "host", null);
        int port = intValue(source, "port", DEFAULT_PORT);
        if (port < 1 || port > 65535) {
            throw parameter("Elasticsearch 端口必须在 1 到 65535 之间");
        }
        if (endpoints.isEmpty()) {
            if (host == null || host.isBlank()) {
                throw parameter("请输入 Elasticsearch 主机地址或 hosts 节点列表");
            }
            endpoints.add(normalizeEndpoint(scheme + "://" + host.trim() + ":" + port, scheme));
        }

        List<String> hosts = List.copyOf(endpoints);
        URI primary = URI.create(hosts.get(0));
        ObjectNode normalized = MAPPER.createObjectNode();
        normalized.put("dbType", type());
        normalized.put("scheme", primary.getScheme());
        normalized.put("host", primary.getHost());
        normalized.put("port", primary.getPort() > 0 ? primary.getPort() : defaultPort(primary.getScheme()));
        ArrayNode hostArray = normalized.putArray("hosts");
        hosts.forEach(hostArray::add);

        String username = trimToNull(text(source, "username", text(source, "user", null)));
        String password = textAllowEmpty(source, "password", textAllowEmpty(source, "passwd", null));
        if (username != null) normalized.put("username", username);
        if (password != null) normalized.put("password", password);

        return new ElasticsearchConnection(type(), hosts, username, password, write(normalized));
    }

    @Override
    public void testConnection(DataSourceConnection connection, int timeoutSeconds) {
        ElasticsearchConnection elasticsearch = requireConnection(connection);
        JsonNode root = new ElasticsearchHttpClient(elasticsearch, timeoutSeconds).get("/", Operation.CONNECTIVITY);
        String version = root.path("version").path("number").asText(null);
        if (version == null || version.isBlank()) {
            throw new DataSourcePluginException(Operation.CONNECTIVITY, "Elasticsearch 未返回 version.number");
        }
        int actual = major(version);
        if (actual != expectedMajorVersion()) {
            throw new DataSourcePluginException(
                    Operation.CONNECTIVITY,
                    "数据源类型为 " + displayName() + "，但服务端版本为 " + version + "（major=" + actual + "）");
        }
    }

    @Override
    public DataSourceCatalog createCatalog(DataSourceConnection connection, int timeoutSeconds) {
        return new ElasticsearchHttpCatalog(
                requireConnection(connection), Math.max(1, timeoutSeconds), expectedMajorVersion());
    }

    @Override
    public boolean acceptsUrl(String jdbcUrl) {
        // A plain HTTP URL cannot distinguish ES7 from ES8. Versioned plugins require explicit dbType.
        return false;
    }

    private ElasticsearchConnection requireConnection(DataSourceConnection connection) {
        if (!(connection instanceof ElasticsearchConnection elasticsearch) || !type().equals(elasticsearch.type())) {
            throw parameter("Elasticsearch 连接类型不匹配：" + type());
        }
        return elasticsearch;
    }

    private ObjectNode parseObject(String json) {
        if (json == null || json.isBlank()) throw parameter("Elasticsearch 连接参数不能为空");
        try {
            JsonNode value = MAPPER.readTree(json);
            if (value == null || !value.isObject()) {
                throw parameter("Elasticsearch 连接参数必须是 JSON 对象");
            }
            return (ObjectNode) value;
        } catch (JsonProcessingException exception) {
            throw new DataSourcePluginException(Operation.PARAMETER, "Elasticsearch 连接参数不是有效 JSON", exception);
        }
    }

    private void appendHosts(Set<String> result, JsonNode value, String scheme) {
        if (value == null || value.isNull()) return;
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (item != null && item.isValueNode()) {
                    appendTextHosts(result, item.asText(), scheme);
                }
            }
            return;
        }
        if (value.isValueNode()) appendTextHosts(result, value.asText(), scheme);
    }

    private void appendTextHosts(Set<String> result, String value, String scheme) {
        if (value == null || value.isBlank()) return;
        for (String part : value.split(",")) {
            if (!part.isBlank()) result.add(normalizeEndpoint(part, scheme));
        }
    }

    private String normalizeEndpoint(String value, String defaultScheme) {
        String endpoint = value.trim();
        if (!endpoint.contains("://")) endpoint = defaultScheme + "://" + endpoint;
        while (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new DataSourcePluginException(Operation.PARAMETER, "Elasticsearch 节点地址不合法：" + value, exception);
        }
        if ((!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
                || uri.getHost() == null
                || (uri.getPath() != null && !uri.getPath().isEmpty())
                || uri.getQuery() != null
                || uri.getFragment() != null
                || uri.getUserInfo() != null) {
            throw parameter("Elasticsearch 节点必须是 http(s)://host[:port]：" + value);
        }
        int port = uri.getPort() > 0 ? uri.getPort() : defaultPort(uri.getScheme());
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost() + ":" + port;
    }

    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : DEFAULT_PORT;
    }

    private int major(String version) {
        int separator = version.indexOf('.');
        String value = separator < 0 ? version : version.substring(0, separator);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new DataSourcePluginException(Operation.CONNECTIVITY, "无法识别 Elasticsearch 版本：" + version, exception);
        }
    }

    private int intValue(JsonNode node, String field, int fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        if (value.isIntegralNumber()) return value.asInt();
        try {
            return Integer.parseInt(value.asText().trim());
        } catch (RuntimeException exception) {
            throw parameter(field + " 必须是整数");
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) return fallback;
        String result = value.asText();
        return result == null || result.isBlank() ? fallback : result.trim();
    }

    private String textAllowEmpty(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? fallback : value.asText();
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String write(JsonNode value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DataSourcePluginException(Operation.PARAMETER, "序列化 Elasticsearch 连接参数失败", exception);
        }
    }

    private DataSourcePluginException parameter(String message) {
        return new DataSourcePluginException(Operation.PARAMETER, message);
    }

    private List<FormField> concat(List<FormField> fields, FormField extra) {
        List<FormField> result = new ArrayList<>(fields);
        result.add(extra);
        return List.copyOf(result);
    }

    private List<FormRule> required(String message) {
        return List.of(new FormRule(true, null, null, null, message));
    }

    private FormField field(
            String key,
            String label,
            FieldType type,
            String placeholder,
            Object defaultValue,
            List<FormOption> options,
            List<FormRule> rules) {
        return new FormField(key, label, type, placeholder, defaultValue, options, rules, List.of(), List.of(), null);
    }
}
