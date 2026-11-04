package io.yak.ops.extract.master.transportor.workflow;

import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.enums.WarningType;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowScheduleTriggerRequest {

    private Integer userId;

    private Date scheduleTIme;

    private String timezoneId;

    private Long workflowCode;

    private Integer workflowVersion;

    @Builder.Default
    private FailureStrategy failureStrategy = FailureStrategy.CONTINUE;

    @Builder.Default
    private TaskDependType taskDependType = TaskDependType.TASK_POST;

    @Builder.Default
    private WarningType warningType = WarningType.NONE;

    private Integer warningGroupId;

    @Builder.Default
    private Priority workflowInstancePriority = Priority.MEDIUM;

    private String workerGroup;

    private String tenantCode;

    private Long environmentCode;

    @Builder.Default
    private Flag dryRun = Flag.NO;

}
