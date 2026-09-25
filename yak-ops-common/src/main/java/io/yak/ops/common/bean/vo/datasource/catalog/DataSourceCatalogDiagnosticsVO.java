package io.yak.ops.common.bean.vo.datasource.catalog;

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
    private List<DataSourceCatalogOperationVO> operations;
}
