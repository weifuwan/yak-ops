package io.yak.ops.modeling.api.vo;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Project-scoped aggregated statistics for the data modeling landing page. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelingOverviewVO {

    private ModelStatistics modelStatistics;
    private IndicatorStatistics indicatorStatistics;
    private long totalAssets;
    private List<LatestPublishItem> latestPublished;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelStatistics {
        private long dimension;
        private long detail;
        private long summary;
        private long application;
        private long total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorStatistics {
        private long atom;
        private long derived;
        private long composite;
        private long total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatestPublishItem {
        private String id;
        private String name;
        private String assetType;
        private String assetTypeLabel;
        private String updateBy;
        private Date updateTime;
    }
}
