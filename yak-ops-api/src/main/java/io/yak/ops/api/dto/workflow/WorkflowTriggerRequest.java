package io.yak.ops.api.dto.workflow;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.dao.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class WorkflowTriggerRequest {

    private User loginUser;

    private long projectCode;

    private long workflowDefinitionCode;

    private String startNodes;

    private FailureStrategy failureStrategy;

    private TaskDependType taskDependType;

    private CommandType execType;

    private WarningType warningType;

    private Integer warningGroupId;

    private Priority workflowInstancePriority;

    private String workerGroup;

    private String tenantCode;

    private Long environmentCode;

    private String startParamList;

    private Flag dryRun;
}
