package io.yak.ops.core.execution.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Result of one synchronous SQL execution. SQL semantics remain a caller/policy concern. */
public record SqlExecutionResult(
    SqlExecutionResultType type,
    List<SqlExecutionColumn> columns,
    List<List<Object>> rows,
    long affectedRows,
    boolean truncated,
    SqlExecutionTiming timing) {

  public SqlExecutionResult {
    type = Objects.requireNonNull(type, "type");
    columns = columns == null ? List.of() : List.copyOf(columns);
    rows = immutableRows(rows);
    timing = Objects.requireNonNull(timing, "timing");
  }

  public boolean resultSet() {
    return type == SqlExecutionResultType.RESULT_SET;
  }

  public int returnedRows() {
    return rows.size();
  }

  private static List<List<Object>> immutableRows(List<List<Object>> values) {
    if (values == null || values.isEmpty()) return List.of();
    List<List<Object>> copied = new ArrayList<>(values.size());
    for (List<Object> row : values) {
      List<Object> cells = row == null ? new ArrayList<>() : new ArrayList<>(row);
      copied.add(Collections.unmodifiableList(cells));
    }
    return Collections.unmodifiableList(copied);
  }
}
