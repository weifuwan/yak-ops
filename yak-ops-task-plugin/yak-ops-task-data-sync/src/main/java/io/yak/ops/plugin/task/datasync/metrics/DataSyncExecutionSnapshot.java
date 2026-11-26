package io.yak.ops.plugin.task.datasync.metrics;

import java.util.Collections;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Immutable point-in-time execution and table metrics. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncExecutionSnapshot {

    private Long readRecords;

    private Long snapshotRecords;

    private Long incrementalRecords;

    private Long insertRecords;

    private Long updateRecords;

    private Long deleteRecords;

    private Long committedRecords;

    private Long ddlEvents;

    private Long errorRecords;

    private Long collectedAt;

    private String metricsState;

    private String errorMessage;

    @Builder.Default
    private List<DataSyncTableMetrics> tables = Collections.emptyList();
}
