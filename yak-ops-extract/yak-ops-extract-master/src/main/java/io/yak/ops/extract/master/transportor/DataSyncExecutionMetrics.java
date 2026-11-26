package io.yak.ops.extract.master.transportor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Execution-level CDC record counters. Null means that the metric was unavailable. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncExecutionMetrics {

    private Long readRecords;

    private Long snapshotRecords;

    private Long incrementalRecords;

    private Long insertRecords;

    private Long updateRecords;

    private Long deleteRecords;

    private Long committedRecords;

    private Long ddlEvents;

    private Long errorRecords;
}
