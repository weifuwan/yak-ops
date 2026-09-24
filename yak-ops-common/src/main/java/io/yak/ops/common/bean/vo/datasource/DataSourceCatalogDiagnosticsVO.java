package io.yak.ops.common.bean.vo.datasource;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Catalog 元数据访问的运行诊断信息。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceCatalogDiagnosticsVO {

    /** Catalog 元数据缓存命中次数。 */
    private long cacheHits;

    /** Catalog 元数据缓存未命中次数。 */
    private long cacheMisses;

    /** Catalog 元数据缓存命中率，范围 0 到 1。 */
    private double cacheHitRate;

    /** 各 Catalog 操作的聚合统计。 */
    private List<OperationVO> operations;

    /**
     * 单个 Catalog 操作的聚合运行指标。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationVO {

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
}
