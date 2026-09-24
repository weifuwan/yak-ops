package io.yak.ops.core.execution.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Platform-neutral request for one synchronous SQL execution. */
public record SqlExecutionRequest(
    String dataSourceId,
    String sql,
    List<Object> parameters,
    int maxRows,
    int timeoutSeconds,
    SqlExecutionContext context) {

  public SqlExecutionRequest {
    dataSourceId = requireText(dataSourceId, "dataSourceId");
    sql = requireText(sql, "sql");
    if (maxRows <= 0) throw new IllegalArgumentException("maxRows must be greater than zero");
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("timeoutSeconds must be greater than zero");
    }
    parameters = immutableNullableList(parameters);
    context = Objects.requireNonNull(context, "context");
  }

  public SqlExecutionRequest(
      String dataSourceId,
      String sql,
      int maxRows,
      int timeoutSeconds,
      SqlExecutionContext context) {
    this(dataSourceId, sql, List.of(), maxRows, timeoutSeconds, context);
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  private static List<Object> immutableNullableList(List<Object> values) {
    if (values == null || values.isEmpty()) return List.of();
    return Collections.unmodifiableList(new ArrayList<>(values));
  }
}
