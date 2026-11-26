package io.yak.ops.api.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSyncMetricsSnapshotVO {

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
