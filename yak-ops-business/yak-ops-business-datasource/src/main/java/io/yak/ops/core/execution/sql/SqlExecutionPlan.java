package io.yak.ops.core.execution.sql;

import java.util.List;
import java.util.Objects;

/**
 * Explicit sequence of statements for one tracked execution.
 *
 * <p>The runtime intentionally does not split SQL text on semicolons. Callers that want multiple
 * statements must provide the statement boundaries explicitly.
 */
public record SqlExecutionPlan(
    String dataSourceId,
    List<SqlStatementRequest> statements,
    SqlExecutionContext context,
    SqlTransactionMode transactionMode) {

  public SqlExecutionPlan {
    if (dataSourceId == null || dataSourceId.isBlank()) {
      throw new IllegalArgumentException("dataSourceId must not be blank");
    }
    dataSourceId = dataSourceId.trim();
    statements = statements == null ? List.of() : List.copyOf(statements);
    if (statements.isEmpty()) {
      throw new IllegalArgumentException("statements must not be empty");
    }
    if (statements.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("statements must not contain null");
    }
    context = Objects.requireNonNull(context, "context");
    transactionMode = transactionMode == null
        ? SqlTransactionMode.AUTO_COMMIT
        : transactionMode;
  }

  /** Convenience overload for the common auto-commit execution mode. */
  public SqlExecutionPlan(
      String dataSourceId,
      List<SqlStatementRequest> statements,
      SqlExecutionContext context) {
    this(dataSourceId, statements, context, SqlTransactionMode.AUTO_COMMIT);
  }

  public static SqlExecutionPlan single(SqlExecutionRequest request) {
    Objects.requireNonNull(request, "request");
    return new SqlExecutionPlan(
        request.dataSourceId(),
        List.of(SqlStatementRequest.from(request)),
        request.context(),
        SqlTransactionMode.AUTO_COMMIT);
  }
}
