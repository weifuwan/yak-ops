package io.yak.ops.business.datasource.execution.audit;

import io.yak.ops.core.execution.sql.SqlStatementType;
import java.util.List;

/** Business read projection for SQL execution observability summary. */
public record SqlExecutionAuditSummary(
        long total,
        long succeeded,
        long failed,
        long cancelled,
        long timedOut,
        double successRate,
        double avgDurationMs,
        long maxDurationMs,
        long p95DurationMs,
        long returnedRows,
        long affectedRows,
        List<StatementTypeCount> statementTypes) {

    public SqlExecutionAuditSummary {
        statementTypes = statementTypes == null ? List.of() : List.copyOf(statementTypes);
    }

    public record StatementTypeCount(SqlStatementType statementType, long count) {}
}
