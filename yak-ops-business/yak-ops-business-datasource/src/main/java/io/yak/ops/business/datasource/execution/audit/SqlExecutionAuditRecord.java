package io.yak.ops.business.datasource.execution.audit;

import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
import java.time.LocalDateTime;

/** Business read projection for one SQL execution audit row. */
public record SqlExecutionAuditRecord(
        String executionId,
        String dataSourceId,
        SqlExecutionCaller caller,
        String callerReference,
        String operatorName,
        SqlTransactionMode transactionMode,
        SqlExecutionStatus status,
        int statementCount,
        int succeededStatementCount,
        long returnedRows,
        long affectedRows,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long durationMs,
        String errorMessage) {}
