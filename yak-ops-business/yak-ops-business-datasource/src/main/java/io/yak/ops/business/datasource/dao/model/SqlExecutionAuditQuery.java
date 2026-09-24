package io.yak.ops.business.datasource.dao.model;

import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
import java.time.LocalDateTime;
import lombok.Getter;

/** DAO-level filters for SQL execution observability queries. */
@Getter
public final class SqlExecutionAuditQuery {

  private final int pageNo;
  private final int pageSize;
  private final String executionId;
  private final String dataSourceId;
  private final SqlExecutionCaller caller;
  private final String callerReference;
  private final String operatorName;
  private final SqlExecutionStatus status;
  private final SqlTransactionMode transactionMode;
  private final SqlStatementType statementType;
  private final String sqlFingerprint;
  private final Long minDurationMs;
  private final LocalDateTime startedFrom;
  private final LocalDateTime startedTo;

  public SqlExecutionAuditQuery(
      int pageNo,
      int pageSize,
      String executionId,
      String dataSourceId,
      SqlExecutionCaller caller,
      String callerReference,
      String operatorName,
      SqlExecutionStatus status,
      SqlTransactionMode transactionMode,
      SqlStatementType statementType,
      String sqlFingerprint,
      Long minDurationMs,
      LocalDateTime startedFrom,
      LocalDateTime startedTo) {
    this.pageNo = Math.max(1, pageNo);
    this.pageSize = Math.min(200, Math.max(1, pageSize));
    this.executionId = normalize(executionId);
    this.dataSourceId = normalize(dataSourceId);
    this.caller = caller;
    this.callerReference = normalize(callerReference);
    this.operatorName = normalize(operatorName);
    this.status = status;
    this.transactionMode = transactionMode;
    this.statementType = statementType;
    this.sqlFingerprint = normalize(sqlFingerprint);
    this.minDurationMs = minDurationMs == null ? null : Math.max(0L, minDurationMs);
    this.startedFrom = startedFrom;
    this.startedTo = startedTo;
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }
}
