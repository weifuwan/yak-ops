package io.yak.ops.api.vo;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSyncExecutionReportVO {

    private String executionId;
    private String jobId;
    private String deploymentMode;
    private String clusterId;
    private String executionState;
    private String metricsState;
    private Long latestSequence;
    private Long finalSequence;
    private Long latestCollectedAt;
    private Long finalCollectedAt;
    private boolean finalized;
    private String metricsError;
    private DataSyncMetricsSnapshotVO latestSnapshot;
    private DataSyncMetricsSnapshotVO finalSnapshot;
    private List<DataSyncTableReportVO> tables;
}
