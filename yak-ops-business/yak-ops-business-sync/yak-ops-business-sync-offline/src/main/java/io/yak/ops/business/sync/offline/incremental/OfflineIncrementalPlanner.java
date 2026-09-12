package io.yak.ops.business.sync.offline.incremental;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogQueryResult;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.ReadMode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.cursor.OfflineCursorGateway;
import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Types;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Freezes the upper bound for one single-table first-full cursor batch. */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineIncrementalPlanner {

  public static final String CURSOR_ID = "timestamp-incremental";
  private static final String UPPER_ALIAS = "yak_incremental_upper";
  private static final Pattern SAFE_COLUMN = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");
  private static final Pattern SAFE_TABLE =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*)*");
  private static final Pattern ISO_TIME =
      Pattern.compile(
          "\\d{4}-\\d{2}-\\d{2}(?:[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?)?");

  private final ObjectMapper objectMapper;
  private final DataSourceCatalogReader catalogReader;
  private final OfflineCursorGateway cursorGateway;

  public OfflineIncrementalPlanner(
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper,
      DataSourceCatalogReader catalogReader,
      OfflineCursorGateway cursorGateway) {
    this.objectMapper = objectMapper;
    this.catalogReader = catalogReader;
    this.cursorGateway = cursorGateway;
  }

  public BatchScope plan(long taskId, String definitionSnapshotJson) {
    JsonNode definition = read(definitionSnapshotJson);
    JsonNode incremental = definition.path("incremental");
    if (!incremental.path("enabled").asBoolean(false)) {
      return BatchScope.fullSelection();
    }
    validateConfiguration(definition);

    long dataSourceId = positiveId(definition.path("source").path("dataSourceId"));
    JsonNode sourceConfig = definition.path("source").path("config");
    String table = safeTable(requiredText(sourceConfig, "table", "来源表不能为空"));
    String column = safeColumn(requiredText(incremental, "column", "增量字段不能为空"));
    CatalogReadRequest tableRequest =
        new CatalogReadRequest(ReadMode.TABLE, table, null, List.of());
    CatalogColumn metadata =
        catalogReader.listColumn(dataSourceId, tableRequest).stream()
            .filter(item -> item.name().equalsIgnoreCase(column))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("来源表不存在增量字段：" + column));
    validateColumnType(metadata);

    String signature = digest(dataSourceId + "|" + table + "|" + column);
    Optional<OfflineSyncCursor> existing = cursorGateway.find(taskId, CURSOR_ID);
    existing.ifPresent(cursor -> validateRoute(cursor, column, signature));
    DataSourceDbType sourceType =
        DataSourceDbType.parse(requiredText(definition.path("source"), "dbType", "来源类型不能为空"));
    String upper = queryUpper(dataSourceId, table, column, sourceType);
    if (upper == null) {
      return BatchScope.emptySelection();
    }
    validateValue(metadata, upper);

    if (existing.isEmpty()) {
      return BatchScope.incrementalBootstrap(CURSOR_ID, column, signature, upper);
    }
    String lower = existing.orElseThrow().position();
    validateValue(metadata, lower);
    int comparison = upper.compareTo(lower);
    if (comparison < 0) {
      throw new IllegalStateException(
          "来源最大游标小于已提交游标，拒绝回退：sourceMax=" + upper + ", cursor=" + lower);
    }
    if (comparison == 0) {
      return BatchScope.emptySelection();
    }
    return BatchScope.incrementalRange(CURSOR_ID, column, signature, lower, upper);
  }

  public void validateConfiguration(JsonNode definition) {
    JsonNode incremental = definition.path("incremental");
    if (!incremental.path("enabled").asBoolean(false)) return;
    if (!"GUIDE_SINGLE".equalsIgnoreCase(definition.path("basic").path("mode").asText())) {
      throw new IllegalArgumentException("全量 + 游标增量首版仅支持单表同步");
    }
    if (!"MAX_TIMESTAMP".equalsIgnoreCase(incremental.path("strategy").asText())) {
      throw new IllegalArgumentException("仅支持 MAX_TIMESTAMP 增量策略");
    }
    if (!"SOURCE_CURRENT_MAX".equalsIgnoreCase(
        incremental.path("bootstrapMode").asText("SOURCE_CURRENT_MAX"))) {
      throw new IllegalArgumentException("首版仅支持从来源当前 MAX 建立初始游标");
    }
    JsonNode source = definition.path("source");
    if (!"jdbc".equalsIgnoreCase(source.path("connectorId").asText())) {
      throw new IllegalArgumentException("全量 + 游标增量首版仅支持 JDBC 来源");
    }
    JsonNode sourceConfig = source.path("config");
    if (!"table".equalsIgnoreCase(sourceConfig.path("readMode").asText("table"))) {
      throw new IllegalArgumentException("全量 + 游标增量暂不支持自定义 SQL");
    }
    safeTable(requiredText(sourceConfig, "table", "来源表不能为空"));
    safeColumn(requiredText(incremental, "column", "增量字段不能为空"));

    JsonNode sinkConfig = definition.path("sink").path("config");
    if (!"upsert".equalsIgnoreCase(sinkConfig.path("writeMode").asText())) {
      throw new IllegalArgumentException("全量 + 游标增量要求目标端使用 UPSERT");
    }
    if (!StringUtils.hasText(sinkConfig.path("primaryKey").asText())) {
      throw new IllegalArgumentException("全量 + 游标增量要求目标端配置主键");
    }
  }

  private String queryUpper(
      long dataSourceId, String table, String column, DataSourceDbType sourceType) {
    String sql =
        "SELECT MAX("
            + JdbcIdentifierQuoter.quote(sourceType, column)
            + ") AS "
            + UPPER_ALIAS
            + " FROM "
            + JdbcIdentifierQuoter.quotePath(sourceType, table);
    CatalogQueryResult result =
        catalogReader.preview(
            dataSourceId, new CatalogReadRequest(ReadMode.SQL, null, sql, List.of()));
    if (result.rows().isEmpty()) return null;
    Object value = valueIgnoreCase(result.rows().get(0), UPPER_ALIAS);
    String normalized = value == null ? null : String.valueOf(value).trim();
    return StringUtils.hasText(normalized) ? normalized : null;
  }

  private void validateColumnType(CatalogColumn column) {
    boolean temporal =
        switch (column.jdbcType()) {
          case Types.DATE,
              Types.TIME,
              Types.TIME_WITH_TIMEZONE,
              Types.TIMESTAMP,
              Types.TIMESTAMP_WITH_TIMEZONE -> true;
          default -> false;
        };
    String type = String.valueOf(column.typeName()).toUpperCase(Locale.ROOT);
    boolean character =
        column.jdbcType() == Types.CHAR
            || column.jdbcType() == Types.VARCHAR
            || column.jdbcType() == Types.LONGVARCHAR
            || type.contains("CHAR")
            || type.contains("TEXT");
    if (!temporal && !character) {
      throw new IllegalArgumentException("增量字段仅支持日期、时间戳或 ISO 字符时间，实际类型=" + column.typeName());
    }
  }

  private void validateValue(CatalogColumn column, String value) {
    String type = String.valueOf(column.typeName()).toUpperCase(Locale.ROOT);
    boolean character =
        column.jdbcType() == Types.CHAR
            || column.jdbcType() == Types.VARCHAR
            || column.jdbcType() == Types.LONGVARCHAR
            || type.contains("CHAR")
            || type.contains("TEXT");
    if (character && !ISO_TIME.matcher(value).matches()) {
      throw new IllegalArgumentException("字符型增量字段必须保存可按字典序排序的 ISO 时间值：" + value);
    }
  }

  private void validateRoute(OfflineSyncCursor cursor, String column, String signature) {
    if (!cursor.sourceColumn().equals(column)) {
      throw new IllegalStateException("已有游标绑定的增量字段不同，拒绝执行");
    }
    if (cursor.sourceSignature() != null && !cursor.sourceSignature().equals(signature)) {
      throw new IllegalStateException("已有游标绑定的来源路由不同，拒绝执行");
    }
  }

  private long positiveId(JsonNode value) {
    long id = value.asLong(0L);
    if (id <= 0L && value.isTextual()) {
      try {
        id = Long.parseLong(value.asText());
      } catch (NumberFormatException ignored) {
        id = 0L;
      }
    }
    if (id <= 0L) throw new IllegalArgumentException("增量任务缺少来源数据源");
    return id;
  }

  private String safeColumn(String value) {
    String normalized = value.trim();
    if (!SAFE_COLUMN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("增量字段不是安全标识符：" + normalized);
    }
    return normalized;
  }

  private String safeTable(String value) {
    String normalized = value.trim();
    if (!SAFE_TABLE.matcher(normalized).matches()) {
      throw new IllegalArgumentException("来源表不是安全标识符：" + normalized);
    }
    return normalized;
  }

  private String requiredText(JsonNode node, String field, String message) {
    String value = node.path(field).asText();
    if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
    return value.trim();
  }

  private Object valueIgnoreCase(Map<String, Object> row, String key) {
    for (Map.Entry<String, Object> entry : row.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
    }
    return null;
  }

  private JsonNode read(String json) {
    try {
      JsonNode value = objectMapper.readTree(json);
      if (value == null || !value.isObject()) {
        throw new IllegalArgumentException("任务定义必须是 JSON 对象");
      }
      return value;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("任务定义 JSON 已损坏", exception);
    }
  }

  private String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("生成来源路由摘要失败", exception);
    }
  }
}
