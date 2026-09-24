package io.yak.ops.dao.model.datasource;

import lombok.Getter;
import lombok.Setter;

/**
 * 承载 SQL execution 审计统计查询的数据库聚合结果。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Getter
@Setter
public class SqlExecutionAuditSummaryRow {

    /** 满足筛选条件的执行总数。 */
    private long total;

    /** 执行状态为 SUCCEEDED 的数量。 */
    private long succeeded;

    /** 执行状态为 FAILED 的数量。 */
    private long failed;

    /** 执行状态为 CANCELLED 的数量。 */
    private long cancelled;

    /** 执行状态为 TIMED_OUT 的数量。 */
    private long timedOut;

    /** 平均执行耗时，单位毫秒。 */
    private double avgDurationMs;

    /** 最大执行耗时，单位毫秒。 */
    private long maxDurationMs;

    /** 累计返回的数据行数。 */
    private long returnedRows;

    /** 累计影响的数据行数。 */
    private long affectedRows;
}
