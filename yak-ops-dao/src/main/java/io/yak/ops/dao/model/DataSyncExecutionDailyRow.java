package io.yak.ops.dao.model;

import lombok.Data;

@Data
public class DataSyncExecutionDailyRow {

    private String bucketDate;
    private Integer executeType;
    private Integer state;
    private long instanceCount;
}
