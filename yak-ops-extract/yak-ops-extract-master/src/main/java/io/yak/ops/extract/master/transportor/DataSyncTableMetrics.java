package io.yak.ops.extract.master.transportor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Table-level CDC counters transported from Worker to Master. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncTableMetrics {

    private String sourceTable;

    private String sinkTable;

    private Long readRecords;

    private Long snapshotRecords;

    private Long incrementalRecords;

    private Long insertRecords;

    private Long updateRecords;

    private Long deleteRecords;

    private Long committedRecords;

    private Long ddlEvents;
}
