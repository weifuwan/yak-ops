package io.yak.ops.api.dto.workflow;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.ComplementDependentMode;
import io.yak.ops.common.enums.ExecutionOrder;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.RunMode;
import io.yak.ops.common.enums.TaskDependType;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.dao.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowBackFillRequest {

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

    private RunMode backfillRunMode;

    private BackfillTime backfillTime;

    private Integer expectedParallelismNumber;

    private ComplementDependentMode backfillDependentMode;

    private boolean allLevelDependent;

    private ExecutionOrder executionOrder;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BackfillTime {

        private String complementStartDate;
        private String complementEndDate;
        private String complementScheduleDateList;

    }

}
