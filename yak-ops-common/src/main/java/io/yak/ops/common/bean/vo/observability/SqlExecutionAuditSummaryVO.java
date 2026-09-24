package io.yak.ops.common.bean.vo.observability;

import java.util.List;

/** Aggregate SQL execution observability summary. */
public record SqlExecutionAuditSummaryVO(
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
        List<StatementTypeCountVO> statementTypes) {

    public SqlExecutionAuditSummaryVO {
        statementTypes = statementTypes == null ? List.of() : List.copyOf(statementTypes);
    }

    public record StatementTypeCountVO(String statementType, long count) {}
}
