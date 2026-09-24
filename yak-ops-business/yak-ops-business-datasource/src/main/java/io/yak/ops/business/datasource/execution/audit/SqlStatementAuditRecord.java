package io.yak.ops.business.datasource.execution.audit;

import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import java.time.LocalDateTime;

/** Business read projection for one statement inside an audited SQL execution. */
public record SqlStatementAuditRecord(
        String statementId,
        int statementIndex,
        SqlStatementType statementType,
        String sqlFingerprint,
        String sqlPreview,
        SqlStatementStatus status,
        String resultType,
        long returnedRows,
        long affectedRows,
        boolean truncated,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long durationMs,
        String errorMessage) {}
