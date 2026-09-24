package io.yak.ops.plugin.database.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.catalog.DataSourceTablePath;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将 Elasticsearch Index、Alias 和 Mapping 映射为统一 Catalog 元数据。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
final class ElasticsearchHttpCatalog implements DataSourceCatalog {

    private final ElasticsearchHttpClient client;
    private final int expectedMajor;
    private volatile boolean versionChecked;

    ElasticsearchHttpCatalog(ElasticsearchConnection connection, int timeoutSeconds, int expectedMajor) {
        this(new ElasticsearchHttpClient(connection, timeoutSeconds), expectedMajor);
    }

    ElasticsearchHttpCatalog(ElasticsearchHttpClient client, int expectedMajor) {
        this.client = client;
        this.expectedMajor = expectedMajor;
    }

    @Override
    public List<String> listDatabases() {
        ensureVersion();
        return List.of(ElasticsearchConnection.VIRTUAL_DATABASE);
    }

    @Override
    public List<String> listSchemas(String database) {
        requireVirtualDatabase(database);
        ensureVersion();
        return List.of();
    }

    @Override
    public List<DataSourceTable> listTables(DataSourceCatalogQuery query) {
        requireVirtualDatabase(query == null ? null : query.getDatabase());
        ensureVersion();
        JsonNode aliases = client.get("/_aliases", Operation.CATALOG);

        Map<String, Set<String>> aliasTargets = new LinkedHashMap<>();
        List<DataSourceTable> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> indices = aliases.fields();
        while (indices.hasNext()) {
            Map.Entry<String, JsonNode> entry = indices.next();
            String index = entry.getKey();
            if (matches(query, index)) result.add(table(index, "INDEX", null));
            JsonNode aliasNode = entry.getValue().path("aliases");
            if (aliasNode.isObject()) {
                aliasNode.fieldNames().forEachRemaining(alias -> aliasTargets
                        .computeIfAbsent(alias, ignored -> new LinkedHashSet<>())
                        .add(index));
            }
        }

        for (Map.Entry<String, Set<String>> entry : aliasTargets.entrySet()) {
            if (entry.getValue().size() == 1 && matches(query, entry.getKey())) {
                result.add(table(entry.getKey(), "ALIAS", "alias -> " + entry.getValue().iterator().next()));
            }
        }

        result.sort(Comparator.comparing(DataSourceTable::getName, String.CASE_INSENSITIVE_ORDER));
        Integer limit = query == null ? null : query.getLimit();
        return limit == null || result.size() <= limit ? List.copyOf(result) : List.copyOf(result.subList(0, limit));
    }

    @Override
    public List<DataSourceColumn> listColumns(DataSourceTablePath tablePath) {
        if (tablePath == null) throw catalog("Elasticsearch tablePath 不能为空");
        requireVirtualDatabase(tablePath.getDatabase());
        return mappingColumns(tablePath.getTable());
    }

    private List<DataSourceColumn> mappingColumns(String indexOrAlias) {
        ensureVersion();
        String index = requireIndex(indexOrAlias);
        JsonNode mapping = client.get("/" + client.encodePathSegment(index) + "/_mapping", Operation.CATALOG);
        if (!mapping.isObject() || mapping.size() != 1) {
            throw catalog("Elasticsearch index/alias 必须解析到一个 concrete index：" + index);
        }

        JsonNode concrete = mapping.elements().next();
        JsonNode properties = concrete.path("mappings").path("properties");
        if (!properties.isObject()) return List.of();

        List<DataSourceColumn> columns = new ArrayList<>();
        int[] ordinal = new int[] {1};
        flattenProperties(properties, "", columns, ordinal);
        return List.copyOf(columns);
    }

    private void flattenProperties(JsonNode properties, String prefix, List<DataSourceColumn> columns, int[] ordinal) {
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonNode mapping = entry.getValue();
            String type = mapping.path("type").asText(null);
            JsonNode nested = mapping.path("properties");
            if (type == null || type.isBlank()) type = nested.isObject() ? "object" : "unknown";

            columns.add(new DataSourceColumn(name, type, jdbcType(type), null, null, true, ordinal[0]++, false, null));
            if (nested.isObject()) flattenProperties(nested, name, columns, ordinal);
        }
    }

    private int jdbcType(String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "boolean" -> Types.BOOLEAN;
            case "byte" -> Types.TINYINT;
            case "short" -> Types.SMALLINT;
            case "integer" -> Types.INTEGER;
            case "long", "unsigned_long" -> Types.BIGINT;
            case "half_float", "float" -> Types.FLOAT;
            case "double" -> Types.DOUBLE;
            case "scaled_float" -> Types.DECIMAL;
            case "date", "date_nanos" -> Types.TIMESTAMP;
            case "binary" -> Types.VARBINARY;
            default -> Types.VARCHAR;
        };
    }

    private void ensureVersion() {
        if (versionChecked) return;
        synchronized (this) {
            if (versionChecked) return;
            JsonNode root = client.get("/", Operation.CATALOG);
            String version = root.path("version").path("number").asText(null);
            if (version == null || version.isBlank()) throw catalog("Elasticsearch 未返回 version.number");
            int separator = version.indexOf('.');
            String majorText = separator < 0 ? version : version.substring(0, separator);
            int actual;
            try {
                actual = Integer.parseInt(majorText);
            } catch (NumberFormatException exception) {
                throw new DataSourcePluginException(Operation.CATALOG, "无法识别 Elasticsearch 版本：" + version, exception);
            }
            if (actual != expectedMajor) {
                throw catalog("Elasticsearch Catalog 版本不匹配：期望 major=" + expectedMajor + "，实际=" + version);
            }
            versionChecked = true;
        }
    }

    private boolean matches(DataSourceCatalogQuery query, String value) {
        if (query == null || query.getKeyword() == null || query.getKeyword().isBlank()) return true;
        return value.toLowerCase(Locale.ROOT).contains(query.getKeyword().trim().toLowerCase(Locale.ROOT));
    }

    private DataSourceTable table(String name, String type, String remarks) {
        return new DataSourceTable(ElasticsearchConnection.VIRTUAL_DATABASE, null, name, type, remarks);
    }

    private void requireVirtualDatabase(String database) {
        if (database == null || database.isBlank()) return;
        if (!ElasticsearchConnection.VIRTUAL_DATABASE.equalsIgnoreCase(database.trim())) {
            throw catalog("Elasticsearch 仅暴露虚拟 Catalog database: elasticsearch");
        }
    }

    private String requireIndex(String value) {
        if (value == null || value.trim().isEmpty()) throw catalog("Elasticsearch index 不能为空");
        String index = value.trim();
        if (index.contains("*") || index.contains(",")) {
            throw catalog("当前阶段仅支持一个明确的 Elasticsearch index/alias，不支持 wildcard 或多 index");
        }
        return index;
    }

    private DataSourcePluginException catalog(String message) {
        return new DataSourcePluginException(Operation.CATALOG, message);
    }
}
