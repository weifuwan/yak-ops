package io.yak.ops.core.execution.sql;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable point-in-time view of one tracked SQL execution. */
public record SqlExecutionSnapshot(
    String executionId,
    SqlExecutionStatus status,
    String dataSourceId,
    SqlExecutionContext context,
    SqlTransactionMode transactionMode,
    List<SqlStatementSnapshot> statements,
    Instant startedAt,
    Instant finishedAt,
    String errorMessage) {

  public SqlExecutionSnapshot {
    if (executionId == null || executionId.isBlank()) {
      throw new IllegalArgumentException("executionId must not be blank");
    }
    executionId = executionId.trim();
    status = Objects.requireNonNull(status, "status");
    if (dataSourceId == null || dataSourceId.isBlank()) {
      throw new IllegalArgumentException("dataSourceId must not be blank");
    }
    dataSourceId = dataSourceId.trim();
    context = Objects.requireNonNull(context, "context");
    transactionMode = transactionMode == null ? SqlTransactionMode.AUTO_COMMIT : transactionMode;
    statements = statements == null ? List.of() : List.copyOf(statements);
  }

  public boolean terminal() {
    return status.terminal();
  }

  public boolean successful() {
    return status == SqlExecutionStatus.SUCCEEDED;
  }

  public long durationMillis() {
    if (startedAt == null) return 0L;
    Instant end = finishedAt == null ? Instant.now() : finishedAt;
    return Math.max(0L, Duration.between(startedAt, end).toMillis());
  }
}
