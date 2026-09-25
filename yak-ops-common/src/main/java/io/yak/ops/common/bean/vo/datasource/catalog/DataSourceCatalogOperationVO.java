package io.yak.ops.common.bean.vo.datasource.catalog;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个 Catalog 操作的聚合运行指标。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceCatalogOperationVO {

    /** 操作名称。 */
    private String operation;

    /** 调用总次数。 */
    private long total;

    /** 失败次数。 */
    private long failures;

    /** 超过慢操作阈值的次数。 */
    private long slow;

    /** 平均耗时，单位毫秒。 */
    private long averageDurationMs;

    /** 最大耗时，单位毫秒。 */
    private long maxDurationMs;

    /** 最近一次慢调用耗时，单位毫秒。 */
    private Long lastSlowDurationMs;

    /** 最近一次慢调用时间。 */
    private LocalDateTime lastSlowTime;
}
