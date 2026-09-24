package io.yak.ops.common.bean.vo.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源管理总览统计。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceSummaryVO {

    /** 数据源总数。 */
    private long total;

    /** 最近连接状态为 CONNECTED 的数据源数量。 */
    private long connected;

    /** 最近连接状态为 DISCONNECTED 的数据源数量。 */
    private long disconnected;

    /** 连接状态为 UNKNOWN 的数据源数量。 */
    private long unknown;

    /** 当前数据源覆盖的运行环境数量。 */
    private long environmentCount;
}
