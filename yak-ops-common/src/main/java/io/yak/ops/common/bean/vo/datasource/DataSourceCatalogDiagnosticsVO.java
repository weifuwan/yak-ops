package io.yak.ops.common.bean.vo.datasource;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DataSource Catalog 运行诊断信息。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceCatalogDiagnosticsVO {

    private long cacheHits;
    private long cacheMisses;
    private double cacheHitRate;
    private List<OperationVO> operations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationVO {
        private String operation;
        private long total;
        private long failures;
        private long slow;
        private long averageDurationMs;
        private long maxDurationMs;
        private Long lastSlowDurationMs;
        private LocalDateTime lastSlowTime;
    }
}
