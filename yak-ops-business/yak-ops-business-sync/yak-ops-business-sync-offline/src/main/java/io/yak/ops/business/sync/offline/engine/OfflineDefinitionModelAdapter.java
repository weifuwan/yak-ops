package io.yak.ops.business.sync.offline.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * 将 Yak Ops 对外任务定义转换为 JobSpec 工厂使用的内部配置结构。
 *
 * <p>该适配仅作用于构建副本，不会把 config、workflow 或其他执行期结构写回任务定义。
 * Yak Ops 自有的通知、编辑器与增量游标配置也不会进入 Link-Up JobSpec。</p>
 *
 * @author weifuwan
 */
public final class OfflineDefinitionModelAdapter {

  private static final Set<String> TABLE_NAMING_RULES =
      Set.of("SAME_NAME", "PREFIX", "SUFFIX");
  private static final Set<String> WRITE_MODES =
      Set.of("APPEND", "OVERWRITE", "UPSERT");
  private static final Set<String> JDBC_DATASOURCE_OWNED_FIELDS = Set.of(
      "url",
      "jdbcUrl",
      "jdbc_url",
      "driver",
      "driverClassName",
      "driver_class_name",
      "username",
      "user",
      "password",
      "passwd",
      "schema",
      "dialect",
      "compatible_mode",
      "properties",
      "connectionParams",
      "connection_params",
      "connJson",
      "connection_check_timeout_sec",
      "connect_timeout_ms",
      "socket_timeout_ms");
  private static final Set<String> ELASTICSEARCH_DATASOURCE_OWNED_FIELDS = Set.of(
      "url",
      "endpoint",
      "hosts",
      "host",
      "hostname",
      "scheme",
      "protocol",
      "port",
      "username",
      "user",
      "password",
      "passwd",
      "connectionParams",
      "connection_params",
      "connect_timeout_ms",
      "socket_timeout_ms");
  private static final Set<String> MONGODB_DATASOURCE_OWNED_FIELDS = Set.of(
      "uri",
      "url",
      "host",
      "hosts",
      "hostname",
      "port",
      "username",
      "user",
      "password",
      "passwd",
      "authSource",
      "auth_source",
      "connectionParams",
      "connection_params",
      "connect_timeout_ms",
      "socket_timeout_ms");

  private static final List<String> SOURCE_FIELDS = List.of(
      "database",
      "readMode",
      "table",
      "tables",
      "tablePattern",
      "fields",
      "sql",
      "whereCondition",
      "fetchSize");

  private static final List<String> SINK_FIELDS = List.of(
      "database",
      "targetMode",
      "table",
      "targetTableName",
      "tableNamingRule",
      "tablePrefix",
      "tableSuffix",
      "autoCreateTable",
      "writeMode",
      "primaryKey",
      "batchSize",
      "sql");

  private OfflineDefinitionModelAdapter() {
  }

  public static JsonNode forJobSpec(JsonNode definition, ObjectMapper objectMapper) {
    if (definition == null || !definition.isObject()) {
      return definition;
    }
    ObjectNode adapted = (ObjectNode) definition.deepCopy();
    // These are Yak Ops control-plane concerns and are not part of Link-Up's JobSpec contract.
    adapted.remove("notification");
    adapted.remove("editorMeta");
    adapted.remove("incremental");
    String mode = text(adapted.path("basic"), "mode", "GUIDE_SINGLE");
    adaptEndpoint(adapted, "source", mode, objectMapper);
    adaptEndpoint(adapted, "sink", mode, objectMapper);
    return adapted;
  }

  /**
   * 清理由数据源负责维护的连接字段，防止凭据进入 definition_json。
   * 非数据源拥有的 Task Connector options 保持原样。
   */
  public static void sanitizeForPersistence(ObjectNode definition) {
    if (definition == null) {
      return;
    }
    sanitizeEndpoint(definition, "source");
    sanitizeEndpoint(definition, "sink");
  }

  private static void sanitizeEndpoint(ObjectNode definition, String field) {
    JsonNode value = definition.get(field);
    if (value == null || !value.isObject()) {
      return;
    }
    ObjectNode endpoint = (ObjectNode) value;
    String connectorId;
    try {
      connectorId = ConnectorIdResolver.resolve(
          text(endpoint, "connectorId", null),
          text(endpoint, "connectorType", null),
          text(endpoint, "dbType", null),
          null);
    } catch (IllegalArgumentException ignored) {
      return;
    }
    Set<String> datasourceOwnedFields = datasourceOwnedFields(connectorId);
    if (datasourceOwnedFields.isEmpty()) {
      return;
    }

    removeDatasourceOwnedFields(endpoint, datasourceOwnedFields);
    JsonNode options = endpoint.get("options");
    if (options != null && options.isObject()) {
      removeDatasourceOwnedFields((ObjectNode) options, datasourceOwnedFields);
    }
    JsonNode config = endpoint.get("config");
    if (config != null && config.isObject()) {
      ObjectNode configObject = (ObjectNode) config;
      removeDatasourceOwnedFields(configObject, datasourceOwnedFields);
      JsonNode connectorOptions = configObject.get("connectorOptions");
      if (connectorOptions != null && connectorOptions.isObject()) {
        removeDatasourceOwnedFields((ObjectNode) connectorOptions, datasourceOwnedFields);
      }
    }
  }

  private static Set<String> datasourceOwnedFields(String connectorId) {
    if (ConnectorIdResolver.isJdbc(connectorId)) {
      return JDBC_DATASOURCE_OWNED_FIELDS;
    }
    if ("elasticsearch7".equalsIgnoreCase(connectorId)
        || "elasticsearch8".equalsIgnoreCase(connectorId)) {
      return ELASTICSEARCH_DATASOURCE_OWNED_FIELDS;
    }
    if ("mongodb".equalsIgnoreCase(connectorId)) {
      return MONGODB_DATASOURCE_OWNED_FIELDS;
    }
    return Set.of();
  }

  private static void removeDatasourceOwnedFields(ObjectNode node, Set<String> fields) {
    fields.forEach(node::remove);
  }

  private static void adaptEndpoint(
      ObjectNode definition,
      String field,
      String mode,
      ObjectMapper objectMapper) {
    JsonNode value = definition.get(field);
    if (value == null || !value.isObject()) {
      return;
    }

    ObjectNode endpoint = (ObjectNode) value;
    ObjectNode config = endpoint.path("config").isObject()
        ? (ObjectNode) endpoint.path("config").deepCopy()
        : objectMapper.createObjectNode();

    JsonNode options = endpoint.get("options");
    if (options != null && options.isObject()) {
      config.set("connectorOptions", options.deepCopy());
    } else if (!config.path("connectorOptions").isObject()) {
      config.set("connectorOptions", objectMapper.createObjectNode());
    }

    List<String> fields = "source".equals(field) ? SOURCE_FIELDS : SINK_FIELDS;
    for (String key : fields) {
      copy(endpoint, config, key);
    }
    copyManagedOption(config, "fetchSize", "fetch_size");
    copyManagedOption(config, "batchSize", "batch_size");

    if ("GUIDE_MULTI".equalsIgnoreCase(mode)) {
      if ("source".equals(field)) {
        adaptMultiSource(config, objectMapper);
      } else {
        adaptMultiSink(config);
      }
    }

    endpoint.set("config", config);
  }

  private static void adaptMultiSource(ObjectNode config, ObjectMapper objectMapper) {
    String database = trim(text(config, "database", null));
    if (!StringUtils.hasText(database)) {
      throw new IllegalArgumentException("多表同步必须填写来源数据库");
    }

    JsonNode tables = config.get("tables");
    if (tables != null && tables.isArray()) {
      ArrayNode qualified = objectMapper.createArrayNode();
      for (JsonNode tableNode : tables) {
        if (!tableNode.isValueNode()) {
          qualified.add(tableNode.deepCopy());
          continue;
        }
        String table = trim(tableNode.asText(null));
        if (StringUtils.hasText(table)) {
          qualified.add(qualify(database, table));
        }
      }
      config.set("tables", qualified);
    }

    String pattern = trim(text(config, "tablePattern", null));
    if (StringUtils.hasText(pattern)) {
      config.put("tablePattern", qualify(database, pattern));
    }
  }

  private static void adaptMultiSink(ObjectNode config) {
    String database = trim(text(config, "database", null));
    if (!StringUtils.hasText(database)) {
      throw new IllegalArgumentException("多表同步必须填写目标数据库");
    }

    String rule = text(config, "tableNamingRule", "SAME_NAME")
        .trim()
        .toUpperCase(Locale.ROOT);
    if (!TABLE_NAMING_RULES.contains(rule)) {
      throw new IllegalArgumentException("不支持的目标表命名规则：" + rule);
    }

    String prefix = text(config, "tablePrefix", "").trim();
    String suffix = text(config, "tableSuffix", "").trim();
    if ("PREFIX".equals(rule) && !StringUtils.hasText(prefix)) {
      throw new IllegalArgumentException("目标表命名规则为 PREFIX 时必须填写 tablePrefix");
    }
    if ("SUFFIX".equals(rule) && !StringUtils.hasText(suffix)) {
      throw new IllegalArgumentException("目标表命名规则为 SUFFIX 时必须填写 tableSuffix");
    }

    String writeMode = text(config, "writeMode", "APPEND")
        .trim()
        .toUpperCase(Locale.ROOT);
    if (!WRITE_MODES.contains(writeMode)) {
      throw new IllegalArgumentException("不支持的多表写入模式：" + writeMode);
    }
    if ("UPSERT".equals(writeMode)
        && !StringUtils.hasText(text(config, "primaryKey", null))) {
      throw new IllegalArgumentException("UPSERT 写入模式必须配置主键字段");
    }

    String tableTemplate = "${table_name}";
    if ("PREFIX".equals(rule)) {
      tableTemplate = prefix + tableTemplate;
    } else if ("SUFFIX".equals(rule)) {
      tableTemplate = tableTemplate + suffix;
    }
    tableTemplate = database + "." + tableTemplate;

    config.put("targetTableName", tableTemplate);
    config.put("tableNamingRule", rule.toLowerCase(Locale.ROOT));
    config.put("writeMode", writeMode.toLowerCase(Locale.ROOT));
  }

  private static void copyManagedOption(
      ObjectNode config,
      String configField,
      String optionField) {
    if (config.hasNonNull(configField)) {
      return;
    }
    JsonNode value = config.path("connectorOptions").get(optionField);
    if (value != null && !value.isNull()) {
      config.set(configField, value.deepCopy());
    }
  }

  private static String qualify(String database, String table) {
    if (table.contains(".")) {
      return table;
    }
    return database + "." + table;
  }

  private static void copy(ObjectNode source, ObjectNode target, String field) {
    JsonNode value = source.get(field);
    if (value != null && !value.isNull()) {
      target.set(field, value.deepCopy());
    }
  }

  private static String text(JsonNode node, String field, String fallback) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull() || !value.isValueNode()) {
      return fallback;
    }
    return value.asText(fallback);
  }

  private static String trim(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
