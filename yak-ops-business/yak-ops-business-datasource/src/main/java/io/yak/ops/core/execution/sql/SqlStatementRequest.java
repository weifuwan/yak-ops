package io.yak.ops.core.execution.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit statement request used by a tracked SQL execution plan. */
public record SqlStatementRequest(
    String sql,
    List<Object> parameters,
    int maxRows,
    int timeoutSeconds) {

  public SqlStatementRequest {
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("sql must not be blank");
    }
    sql = sql.trim();
    if (maxRows <= 0) throw new IllegalArgumentException("maxRows must be greater than zero");
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("timeoutSeconds must be greater than zero");
    }
    parameters = immutableNullableList(parameters);
  }

  public SqlStatementRequest(String sql, int maxRows, int timeoutSeconds) {
    this(sql, List.of(), maxRows, timeoutSeconds);
  }

  static SqlStatementRequest from(SqlExecutionRequest request) {
    return new SqlStatementRequest(
        request.sql(), request.parameters(), request.maxRows(), request.timeoutSeconds());
  }

  private static List<Object> immutableNullableList(List<Object> values) {
    if (values == null || values.isEmpty()) return List.of();
    return Collections.unmodifiableList(new ArrayList<>(values));
  }
}
