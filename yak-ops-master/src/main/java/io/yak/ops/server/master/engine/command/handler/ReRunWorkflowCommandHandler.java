package io.yak.ops.server.master.engine.command.handler;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.server.master.config.MasterConfig;
import io.yak.ops.server.master.runner.WorkflowExecuteContext.WorkflowExecuteContextBuilder;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This handler used to handle {@link CommandType#REPEAT_RUNNING} which will rerun the workflow instance.
 */
@Component
public class ReRunWorkflowCommandHandler extends RunWorkflowCommandHandler {

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Autowired
    private TaskInstanceDao taskInstanceDao;

    @Autowired
    private MasterConfig masterConfig;

    /**
     * Generate the repeat running workflow instance.
     * <p> Will use the origin workflow instance, but will update the following fields. Need to note we cannot not
     * update the command params here, since this will make the origin command params lost.
     * <ul>
     *     <li>state</li>
     *     <li>command type</li>
     *     <li>start time</li>
     *     <li>restart time</li>
     *     <li>end time</li>
     *     <li>run times</li>
     * </ul>
     */
    @Override
    protected void assembleWorkflowInstance(final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        final Command command = workflowExecuteContextBuilder.getCommand();
        final int workflowInstanceId = command.getWorkflowInstanceId();
        final WorkflowInstance workflowInstance = workflowInstanceDao.queryOptionalById(workflowInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Cannot find WorkflowInstance:" + workflowInstanceId));
        workflowInstance.setVarPool(null);
        workflowInstance.setStateWithDesc(WorkflowExecutionStatus.RUNNING_EXECUTION, command.getCommandType().name());
        workflowInstance.setCommandType(command.getCommandType());
        workflowInstance.setRestartTime(new Date());
        workflowInstance.setHost(masterConfig.getMasterAddress());
        workflowInstance.setEndTime(null);
        workflowInstance.setRunTimes(workflowInstance.getRunTimes() + 1);
        workflowInstanceDao.updateById(workflowInstance);

        workflowExecuteContextBuilder.setWorkflowInstance(workflowInstance);
    }

    /**
     * Generate the workflow execution graph.
     * <p> Will clear the history task instance and assembly the start tasks into the WorkflowExecutionGraph.
     */
    @Override
    protected void assembleWorkflowExecutionGraph(final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        markAllTaskInstanceInvalid(workflowExecuteContextBuilder);
        super.assembleWorkflowExecutionGraph(workflowExecuteContextBuilder);
    }

    private void markAllTaskInstanceInvalid(final WorkflowExecuteContextBuilder workflowExecuteContextBuilder) {
        final WorkflowInstance workflowInstance = workflowExecuteContextBuilder.getWorkflowInstance();
        final List<TaskInstance> taskInstances = getValidTaskInstance(workflowInstance);
        taskInstanceDao.markTaskInstanceInvalid(taskInstances);
    }

    @Override
    public CommandType commandType() {
        return CommandType.REPEAT_RUNNING;
    }
}
