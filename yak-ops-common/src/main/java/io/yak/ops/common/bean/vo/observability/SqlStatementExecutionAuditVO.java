package io.yak.ops.common.bean.vo.observability;

import java.time.LocalDateTime;

/** Statement-level SQL observability view. */
public record SqlStatementExecutionAuditVO(
        String statementId,
        int statementIndex,
        String statementType,
        String sqlFingerprint,
        String sqlPreview,
        String status,
        String resultType,
        long returnedRows,
        long affectedRows,
        boolean truncated,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long durationMs,
        String errorMessage) {}
