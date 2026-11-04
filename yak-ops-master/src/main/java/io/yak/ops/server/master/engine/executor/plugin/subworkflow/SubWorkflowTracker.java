package io.yak.ops.server.master.engine.executor.plugin.subworkflow;

import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubWorkflowTracker {

    private final WorkflowInstanceDao workflowInstanceDao;

    private final SubWorkflowLogicTaskRuntimeContext subWorkflowLogicTaskRuntimeContext;

    public SubWorkflowTracker(final SubWorkflowLogicTaskRuntimeContext subWorkflowLogicTaskRuntimeContext,
                              final WorkflowInstanceDao workflowInstanceDao) {
        this.subWorkflowLogicTaskRuntimeContext = subWorkflowLogicTaskRuntimeContext;
        this.workflowInstanceDao = workflowInstanceDao;
    }

    public @NonNull TaskExecutionStatus getSubWorkflowState() {
        final Integer subWorkflowInstanceId = subWorkflowLogicTaskRuntimeContext.getSubWorkflowInstanceId();
        final WorkflowInstance subWorkflowInstance = workflowInstanceDao.queryById(subWorkflowInstanceId);
        if (subWorkflowInstance == null) {
            log.info("Cannot find the SubWorkflow instance: {}, maybe it has been deleted", subWorkflowInstanceId);
            return TaskExecutionStatus.FAILURE;
        }
        switch (subWorkflowInstance.getState()) {
            case PAUSE:
                return TaskExecutionStatus.PAUSE;
            case STOP:
                return TaskExecutionStatus.KILL;
            case SUCCESS:
                return TaskExecutionStatus.SUCCESS;
            case FAILURE:
                return TaskExecutionStatus.FAILURE;
            default:
                return TaskExecutionStatus.RUNNING_EXECUTION;
        }
    }

}
