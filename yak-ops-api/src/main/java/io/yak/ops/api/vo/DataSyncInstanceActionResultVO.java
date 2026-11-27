package io.yak.ops.api.vo;

import io.yak.ops.api.enums.DataSyncInstanceAction;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSyncInstanceActionResultVO {

    private DataSyncInstanceAction action;

    private Integer sourceInstanceId;

    private Integer newWorkflowInstanceId;
}
