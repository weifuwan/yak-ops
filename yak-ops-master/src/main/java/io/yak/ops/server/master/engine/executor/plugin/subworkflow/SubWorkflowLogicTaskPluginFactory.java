package io.yak.ops.server.master.engine.executor.plugin.subworkflow;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.task.SubWorkflowLogicTaskChannelFactory;
import io.yak.ops.server.master.engine.IWorkflowRepository;
import io.yak.ops.server.master.engine.executor.plugin.ILogicTaskPluginFactory;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.task.executor.ITaskExecutor;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SubWorkflowLogicTaskPluginFactory implements ILogicTaskPluginFactory<SubWorkflowLogicTask> {

    private final ApplicationContext applicationContext;

    private final IWorkflowRepository workflowRepository;

    public SubWorkflowLogicTaskPluginFactory(final ApplicationContext applicationContext,
                                             final IWorkflowRepository workflowRepository) {
        this.applicationContext = applicationContext;
        this.workflowRepository = workflowRepository;
    }

    @Override
    public SubWorkflowLogicTask createLogicTask(final ITaskExecutor taskExecutor) {
        final TaskExecutionContext taskExecutionContext = taskExecutor.getTaskExecutionContext();
        final int workflowInstanceId = taskExecutionContext.getWorkflowInstanceId();
        final IWorkflowExecution workflowExecution = workflowRepository.get(workflowInstanceId);
        return new SubWorkflowLogicTask(
                taskExecutionContext,
                workflowExecution,
                taskExecutor,
                applicationContext);
    }

    @Override
    public String getTaskType() {
        return SubWorkflowLogicTaskChannelFactory.NAME;
    }
}
