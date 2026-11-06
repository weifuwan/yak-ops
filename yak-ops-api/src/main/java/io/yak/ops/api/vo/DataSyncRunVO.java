package io.yak.ops.api.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DataSyncRunVO {
    private String definitionId;
    private Integer workflowInstanceId;
    private int definitionVersion;
    private String status;
}
