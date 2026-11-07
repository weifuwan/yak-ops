package io.yak.ops.api.vo;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.util.Date;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSyncInstanceVO {

    private String id;
    private String definitionId;
    private String taskName;
    private int definitionVersion;
    private String taskType;
    private String triggerType;
    private TaskExecutionStatus status;
    private Date startTime;
    private Date endTime;
    private Date createTime;
    private String runtimeEngine;
    private String runtimeId;
    private String host;
    private Integer workflowInstanceId;
}
