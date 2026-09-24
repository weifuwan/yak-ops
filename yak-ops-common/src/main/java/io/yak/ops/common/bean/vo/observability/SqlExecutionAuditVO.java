package io.yak.ops.common.bean.vo.observability;

import java.time.LocalDateTime;

/** Execution-level SQL observability view. */
public record SqlExecutionAuditVO(
        String executionId,
        String dataSourceId,
        String caller,
        String callerReference,
        String operatorName,
        String transactionMode,
        String status,
        int statementCount,
        int succeededStatementCount,
        long returnedRows,
        long affectedRows,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long durationMs,
        String errorMessage) {}
