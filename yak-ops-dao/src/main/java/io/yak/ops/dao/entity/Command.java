package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.enums.WarningType;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_ds_command")
public class Command {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("command_type")
    private CommandType commandType;

    @TableField("workflow_definition_code")
    private long workflowDefinitionCode;

    @TableField("workflow_definition_version")
    private int workflowDefinitionVersion;

    @TableField("workflow_instance_id")
    private int workflowInstanceId;

    /**
     * command parameter, format json
     */
    @TableField("command_param")
    private String commandParam;

    @TableField("workflow_instance_priority")
    private Priority workflowInstancePriority;

    @Deprecated
    @TableField("executor_id")
    private int executorId;

    @Deprecated
    @TableField("task_depend_type")
    @Builder.Default
    private TaskDependType taskDependType = TaskDependType.TASK_POST;

    @Deprecated
    @TableField("failure_strategy")
    @Builder.Default
    private FailureStrategy failureStrategy = FailureStrategy.CONTINUE;

    @Deprecated
    @TableField("warning_type")
    private WarningType warningType;

    @Deprecated
    @TableField("warning_group_id")
    private Integer warningGroupId;

    @Deprecated
    @TableField("schedule_time")
    private Date scheduleTime;

    @Deprecated
    @TableField("start_time")
    private Date startTime = new Date();

    @Deprecated
    @TableField("update_time")
    @Builder.Default
    private Date updateTime = new Date();

    @Deprecated
    @TableField("worker_group")
    private String workerGroup;

    @Deprecated
    private String tenantCode;

    @Deprecated
    @TableField("environment_code")
    private Long environmentCode;

    @Deprecated
    @TableField("dry_run")
    private int dryRun;

    public Command(
                   CommandType commandType,
                   TaskDependType taskDependType,
                   FailureStrategy failureStrategy,
                   int executorId,
                   long workflowDefinitionCode,
                   String commandParam,
                   WarningType warningType,
                   int warningGroupId,
                   Date scheduleTime,
                   String workerGroup,
                   Long environmentCode,
                   Priority workflowInstancePriority,
                   int dryRun,
                   int workflowInstanceId,
                   int workflowDefinitionVersion) {
        this.commandType = commandType;
        this.executorId = executorId;
        this.workflowDefinitionCode = workflowDefinitionCode;
        this.commandParam = commandParam;
        this.warningType = warningType;
        this.warningGroupId = warningGroupId;
        this.scheduleTime = scheduleTime;
        this.taskDependType = taskDependType;
        this.failureStrategy = failureStrategy;
        this.startTime = new Date();
        this.updateTime = new Date();
        this.workerGroup = workerGroup;
        this.environmentCode = environmentCode;
        this.workflowInstancePriority = workflowInstancePriority;
        this.dryRun = dryRun;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowDefinitionVersion = workflowDefinitionVersion;
    }
}
