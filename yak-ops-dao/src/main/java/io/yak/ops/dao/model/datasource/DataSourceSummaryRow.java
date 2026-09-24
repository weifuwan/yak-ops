package io.yak.ops.dao.model.datasource;

import lombok.Getter;
import lombok.Setter;

/**
 * 承载数据源统计 SQL 的数据库聚合结果，不作为 HTTP 响应模型。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Getter
@Setter
public class DataSourceSummaryRow {

    /** 数据源总数。 */
    private long total;

    /** 当前连通状态为 CONNECTED 的数据源数量。 */
    private long connected;

    /** 当前连通状态为 DISCONNECTED 的数据源数量。 */
    private long disconnected;

    /** 当前连通状态为 UNKNOWN 的数据源数量。 */
    private long unknown;

    /** 当前数据源覆盖的不同运行环境数量。 */
    private long environmentCount;
}
