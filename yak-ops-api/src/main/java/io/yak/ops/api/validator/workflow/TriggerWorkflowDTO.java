package io.yak.ops.api.validator.workflow;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.RunMode;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.plugin.task.api.model.Property;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerWorkflowDTO {

    private User loginUser;

    private WorkflowDefinition workflowDefinition;

    private List<Long> startNodes;

    private FailureStrategy failureStrategy;

    private TaskDependType taskDependType;

    private CommandType execType;

    private WarningType warningType;

    private Integer warningGroupId;

    private RunMode runMode;

    private Priority workflowInstancePriority;

    private String workerGroup;

    private String tenantCode;

    private Long environmentCode;

    private List<Property> startParamList;

    private Flag dryRun;

}
