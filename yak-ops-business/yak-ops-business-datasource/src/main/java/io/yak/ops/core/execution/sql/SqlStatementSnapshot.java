package io.yak.ops.core.execution.sql;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Immutable lifecycle snapshot for one statement in a tracked execution. */
public record SqlStatementSnapshot(
    String statementId,
    int index,
    String sql,
    SqlStatementType statementType,
    SqlStatementStatus status,
    SqlExecutionResult result,
    String errorMessage,
    Instant startedAt,
    Instant finishedAt) {

  public SqlStatementSnapshot {
    if (statementId == null || statementId.isBlank()) {
      throw new IllegalArgumentException("statementId must not be blank");
    }
    statementId = statementId.trim();
    if (index < 0) throw new IllegalArgumentException("index must not be negative");
    if (sql == null || sql.isBlank()) throw new IllegalArgumentException("sql must not be blank");
    sql = sql.trim();
    statementType = statementType == null ? SqlStatementType.OTHER : statementType;
    status = Objects.requireNonNull(status, "status");
  }

  public boolean terminal() {
    return status.terminal();
  }

  public long durationMillis() {
    if (startedAt == null) return 0L;
    Instant end = finishedAt == null ? Instant.now() : finishedAt;
    return Math.max(0L, Duration.between(startedAt, end).toMillis());
  }
}
