package io.yak.ops.server.master.engine.executor.plugin.switchtask;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.task.SwitchLogicTaskChannelFactory;
import io.yak.ops.server.master.engine.IWorkflowRepository;
import io.yak.ops.server.master.engine.executor.plugin.ILogicTaskPluginFactory;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.exception.LogicTaskInitializeException;
import io.yak.ops.task.executor.ITaskExecutor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SwitchLogicTaskPluginFactory implements ILogicTaskPluginFactory<SwitchLogicTask> {

    @Autowired
    private IWorkflowRepository IWorkflowRepository;

    @Override
    public SwitchLogicTask createLogicTask(final ITaskExecutor taskExecutor) throws LogicTaskInitializeException {
        final TaskExecutionContext taskExecutionContext = taskExecutor.getTaskExecutionContext();
        final int workflowInstanceId = taskExecutionContext.getWorkflowInstanceId();
        IWorkflowExecution workflowExecution =
                IWorkflowRepository.get(workflowInstanceId);
        if (workflowExecution == null) {
            throw new LogicTaskInitializeException(
                    "Cannot find the WorkflowExecuteRunnable by : " + workflowInstanceId);
        }
        return new SwitchLogicTask(workflowExecution, taskExecutionContext);
    }

    @Override
    public String getTaskType() {
        return SwitchLogicTaskChannelFactory.NAME;
    }
}
