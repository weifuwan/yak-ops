package io.yak.ops.business.datasource.dao.model;

import lombok.Data;

/** Database aggregate for SQL execution observability. */
@Data
public class SqlExecutionAuditSummaryRow {
    private long total;
    private long succeeded;
    private long failed;
    private long cancelled;
    private long timedOut;
    private double avgDurationMs;
    private long maxDurationMs;
    private long returnedRows;
    private long affectedRows;
}
