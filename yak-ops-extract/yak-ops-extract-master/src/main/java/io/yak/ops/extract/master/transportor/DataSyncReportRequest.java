package io.yak.ops.extract.master.transportor;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Versioned, idempotent CDC execution report. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncReportRequest {

    private Integer protocolVersion;

    private String executionId;

    private Integer taskInstanceId;

    private String jobId;

    private String deploymentMode;

    private String clusterId;

    private Long sequence;

    private Long submittedAt;

    /** SNAPSHOT or FINAL. */
    private String reportType;

    private String executionState;

    private String metricsState;

    private Long collectedAt;

    private String metricsError;

    private DataSyncExecutionMetrics summary;

    private List<DataSyncTableMetrics> tables;

    /** Latest completed checkpoint observed by the Worker. */
    private DataSyncRecoveryPointReport recoveryPoint;

    /** Recovery point used to start this execution; used to link old and new task instances. */
    private String recoveredFromRecoveryPointId;
}
