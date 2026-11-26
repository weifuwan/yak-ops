package io.yak.ops.service.datasync.model;

import io.yak.ops.extract.master.transportor.DataSyncExecutionMetrics;
import io.yak.ops.extract.master.transportor.DataSyncTableMetrics;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Read model returned to the API layer for one CDC execution. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncExecutionReportDetail {

    private String executionId;

    private Integer taskInstanceId;

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

    private DataSyncExecutionMetrics latestSnapshot;

    private DataSyncExecutionMetrics finalSnapshot;

    private List<DataSyncTableMetrics> tables;
}
