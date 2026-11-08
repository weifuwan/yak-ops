package io.yak.ops.dao.model;

import lombok.Data;

@Data
public class DataSyncOverviewTaskRow {

    private long taskCode;
    private String taskName;
    private String taskParams;
    private int releaseState;
    private Integer latestWorkflowState;
}
