package io.yak.ops.business.datasource.execution.audit;

import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
import java.time.LocalDateTime;

/** Typed read-side criteria for SQL execution audit queries. */
public record SqlExecutionAuditCriteria(
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

    public SqlExecutionAuditCriteria {
        if (pageNo <= 0) throw new IllegalArgumentException("pageNo must be greater than zero");
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be greater than zero");
        if (startedFrom != null && startedTo != null && startedFrom.isAfter(startedTo)) {
            throw new IllegalArgumentException("startedFrom must not be after startedTo");
        }
    }
}
