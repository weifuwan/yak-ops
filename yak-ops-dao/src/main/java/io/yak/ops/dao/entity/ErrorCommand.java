package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.enums.WarningType;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_ds_error_command")
public class ErrorCommand {

    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    private CommandType commandType;

    private long workflowDefinitionCode;

    private int workflowDefinitionVersion;

    private int workflowInstanceId;

    private int executorId;

    private String commandParam;

    private TaskDependType taskDependType;

    private FailureStrategy failureStrategy;

    private WarningType warningType;

    private Integer warningGroupId;

    private Date scheduleTime;

    private Date startTime;

    private Priority workflowInstancePriority;

    private Date updateTime;

    private String message;

    private String workerGroup;

    private String tenantCode;

    private Long environmentCode;

    private int dryRun;

    public ErrorCommand() {
    }

    public ErrorCommand(Command command, String message) {
        this.id = command.getId();
        this.commandType = command.getCommandType();
        this.executorId = command.getExecutorId();
        this.workflowDefinitionCode = command.getWorkflowDefinitionCode();
        this.workflowDefinitionVersion = command.getWorkflowDefinitionVersion();
        this.workflowInstanceId = command.getWorkflowInstanceId();
        this.commandParam = command.getCommandParam();
        this.taskDependType = command.getTaskDependType();
        this.failureStrategy = command.getFailureStrategy();
        this.warningType = command.getWarningType();
        this.warningGroupId = command.getWarningGroupId();
        this.scheduleTime = command.getScheduleTime();
        this.startTime = command.getStartTime();
        this.updateTime = command.getUpdateTime();
        this.workflowInstancePriority = command.getWorkflowInstancePriority();
        this.workerGroup = command.getWorkerGroup();
        this.tenantCode = command.getTenantCode();
        this.environmentCode = command.getEnvironmentCode();
        this.message = message;
        this.dryRun = command.getDryRun();
    }
}
