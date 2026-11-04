package io.yak.ops.server.master.engine.executor.plugin.condition;

import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.task.ConditionsLogicTaskChannelFactory;
import io.yak.ops.server.master.engine.IWorkflowRepository;
import io.yak.ops.server.master.engine.executor.plugin.ILogicTaskPluginFactory;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.task.executor.ITaskExecutor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ConditionLogicTaskPluginFactory implements ILogicTaskPluginFactory<ConditionLogicTask> {

    @Autowired
    private TaskInstanceDao taskInstanceDao;

    @Autowired
    private IWorkflowRepository workflowExecutionMemoryRepository;

    @Override
    public ConditionLogicTask createLogicTask(final ITaskExecutor taskExecutor) {
        final TaskExecutionContext taskExecutionContext = taskExecutor.getTaskExecutionContext();
        final IWorkflowExecution workflowExecution =
                workflowExecutionMemoryRepository.get(taskExecutionContext.getWorkflowInstanceId());
        return new ConditionLogicTask(workflowExecution, taskExecutionContext, taskInstanceDao);
    }

    @Override
    public String getTaskType() {
        return ConditionsLogicTaskChannelFactory.NAME;
    }
}
