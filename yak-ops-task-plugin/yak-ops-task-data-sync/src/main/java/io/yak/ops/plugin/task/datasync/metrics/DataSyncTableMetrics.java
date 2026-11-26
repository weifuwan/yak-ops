package io.yak.ops.plugin.task.datasync.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Table-level CDC counters collected from Flink metric groups. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncTableMetrics {

    private String sourceTable;

    private String sinkTable;

    private String metricsState;

    private Long readRecords;

    private Long snapshotRecords;

    private Long incrementalRecords;

    private Long insertRecords;

    private Long updateRecords;

    private Long deleteRecords;

    private Long committedRecords;

    private Long ddlEvents;
}
