package io.yak.ops.business.sync.offline.execution.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.cursor.OfflineCursorGateway;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.engine.ConnectorIdResolver;
import io.yak.ops.business.sync.offline.execution.OfflineExecutionScopeValidator;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Applies a frozen BatchScope to the frozen logical JobSpec before credential resolution. */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineBatchScopeExecutionAdapter implements OfflineExecutionScopeValidator {

  private static final Pattern SAFE_IDENTIFIER =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*)*");

  private final ObjectMapper objectMapper;
  private final OfflineCursorGateway cursorGateway;

  public OfflineBatchScopeExecutionAdapter(
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper,
      OfflineCursorGateway cursorGateway) {
    this.objectMapper = objectMapper;
    this.cursorGateway = cursorGateway;
  }

  @Override
  public void validate(long taskId, String logicalJobSpecJson, BatchScope scope) {
    apply(taskId, logicalJobSpecJson, scope);
  }

  public String apply(long taskId, String logicalJobSpecJson, BatchScope scope) {
    validateInput(taskId, logicalJobSpecJson);
    if (scope == null
        || scope instanceof BatchScope.FullSelection
        || (scope instanceof BatchScope.IncrementalRange range && range.bootstrapFull())) {
      return logicalJobSpecJson.trim();
    }

    ObjectNode root = parseObject(logicalJobSpecJson);
    if ("OfflineExecutionPlan".equals(root.path("kind").asText())) {
      throw new IllegalStateException(
          "FAN_OUT 当前仅支持 FULL_SELECTION BatchScope；DataWindow / Partition / Cursor 不会跨表猜测路由");
    }
    ObjectNode options = requireScopedSourceOptions(root);
    String predicate = predicate(taskId, options, scope);
    mergeWhereCondition(options, predicate);
    return write(root);
  }

  private void validateInput(long taskId, String logicalJobSpecJson) {
    if (taskId <= 0L) {
      throw new IllegalArgumentException("TaskId 必须大于 0");
    }
    if (!StringUtils.hasText(logicalJobSpecJson)) {
      throw new IllegalArgumentException("logicalJobSpec 不能为空");
    }
  }

  private ObjectNode requireScopedSourceOptions(ObjectNode root) {
    ObjectNode source = requireObject(root.get("source"), "scoped Batch 缺少 source JobSpec");
    if (!ConnectorIdResolver.isJdbc(text(source, "connectorId"))) {
      throw new IllegalStateException("Wave 5 scoped Batch V1 仅支持 JDBC source");
    }

    ObjectNode options = requireObject(source.get("options"), "scoped Batch 缺少 source.options");
    boolean singleTable =
        StringUtils.hasText(text(options, "table_path"))
            && (!options.has("table_list") || options.path("table_list").isEmpty());
    if (!singleTable) {
      throw new IllegalStateException("Wave 5 scoped Batch V1 仅支持单表 source");
    }
    if (StringUtils.hasText(text(options, "query"))) {
      throw new IllegalStateException("自定义 source query 暂不支持叠加 BatchScope，请使用表 + whereCondition");
    }
    return options;
  }

  private void mergeWhereCondition(ObjectNode options, String predicate) {
    String existing = text(options, "where_condition");
    String scoped =
        StringUtils.hasText(existing)
            ? "(" + existing.trim() + ") AND (" + predicate + ")"
            : predicate;
    options.put("where_condition", scoped);
  }

  private String predicate(long taskId, ObjectNode options, BatchScope scope) {
    if (scope instanceof BatchScope.DataWindow window) {
      String column = safeColumn(
          requiredText(
              options,
              "partition_column",
              "DataWindow 需要 source.options.partition_column"));
      return column
          + " >= "
          + literal(time(window.startInclusive()))
          + " AND "
          + column
          + " < "
          + literal(time(window.endExclusive()));
    }

    if (scope instanceof BatchScope.PartitionScope partitions) {
      String column = safeColumn(
          requiredText(
              options,
              "partition_column",
              "PartitionScope 需要 source.options.partition_column"));
      String values =
          partitions.partitions().stream()
              .map(this::literal)
              .reduce((left, right) -> left + ", " + right)
              .orElseThrow();
      return column + " IN (" + values + ")";
    }

    if (scope instanceof BatchScope.CursorRange range) {
      String column = safeColumn(cursorGateway.requireSourceColumn(taskId, range.cursorId()));
      return column
          + " > "
          + literal(range.afterExclusive())
          + " AND "
          + column
          + " <= "
          + literal(range.throughInclusive());
    }

    if (scope instanceof BatchScope.IncrementalRange range) {
      String column = safeColumn(range.sourceColumn());
      return column
          + " > "
          + literal(range.afterExclusive())
          + " AND "
          + column
          + " <= "
          + literal(range.throughInclusive());
    }

    throw new IllegalArgumentException("不支持的 BatchScope：" + scope.getClass().getName());
  }

  private String safeColumn(String value) {
    String normalized = value.trim();
    if (!SAFE_IDENTIFIER.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Scope source column 不是安全标识符：" + normalized);
    }
    return normalized;
  }

  private String literal(String value) {
    return "'" + value.replace("'", "''") + "'";
  }

  private String time(LocalDateTime value) {
    return value.toString().replace('T', ' ');
  }

  private ObjectNode parseObject(String json) {
    try {
      return requireObject(objectMapper.readTree(json), "logicalJobSpec 必须是 JSON 对象");
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("logicalJobSpec JSON 已损坏", exception);
    }
  }

  private ObjectNode requireObject(JsonNode value, String message) {
    if (value == null || !value.isObject()) {
      throw new IllegalStateException(message);
    }
    return (ObjectNode) value;
  }

  private String requiredText(JsonNode node, String field, String message) {
    String value = text(node, field);
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException(message);
    }
    return value.trim();
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() ? null : value.asText(null);
  }

  private String write(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化 scoped logical JobSpec 失败", exception);
    }
  }
}
