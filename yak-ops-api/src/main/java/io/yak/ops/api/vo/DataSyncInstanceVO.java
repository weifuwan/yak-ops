package io.yak.ops.api.vo;

import io.yak.ops.api.enums.DataSyncInstanceAction;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.util.Date;
import java.util.List;

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
    private TaskExecuteType executeType;
    private String triggerType;
    private TaskExecutionStatus status;
    private WorkflowExecutionStatus workflowStatus;
    private List<DataSyncInstanceAction> availableActions;
    private boolean recoveryPointAvailable;
    private Date recoveryPointTime;
    private Integer recoveryPointUsedByWorkflowInstanceId;
    private Integer recoveryPointUsedByInstanceId;
    private String recoveryUnavailableReason;
    private String executionReport;
    private Date startTime;
    private Date endTime;
    private Date createTime;
    private String runtimeEngine;
    private String runtimeId;
    private String host;
    private Integer workflowInstanceId;
}
