package io.yak.ops.server.master.engine.executor.plugin.dependent;

import io.yak.ops.dao.repository.ProjectDao;
import io.yak.ops.dao.repository.TaskDefinitionDao;
import io.yak.ops.dao.repository.TaskInstanceContextDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.task.DependentLogicTaskChannelFactory;
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
public class DependentLogicTaskPluginFactory implements ILogicTaskPluginFactory<DependentLogicTask> {

    @Autowired
    private ProjectDao projectDao;

    @Autowired
    private WorkflowDefinitionDao workflowDefinitionDao;

    @Autowired
    private TaskDefinitionDao taskDefinitionDao;

    @Autowired
    private TaskInstanceDao taskInstanceDao;

    @Autowired
    private TaskInstanceContextDao taskInstanceContextDao;

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Autowired
    private IWorkflowRepository IWorkflowRepository;

    @Override
    public DependentLogicTask createLogicTask(final ITaskExecutor taskExecutor) throws LogicTaskInitializeException {
        final TaskExecutionContext taskExecutionContext = taskExecutor.getTaskExecutionContext();
        final int workflowInstanceId = taskExecutionContext.getWorkflowInstanceId();
        final IWorkflowExecution workflowExecution = IWorkflowRepository.get(workflowInstanceId);
        if (workflowExecution == null) {
            throw new LogicTaskInitializeException("Cannot find the WorkflowExecuteRunnable: " + workflowInstanceId);
        }
        return new DependentLogicTask(
                taskExecutionContext,
                projectDao,
                workflowDefinitionDao,
                taskDefinitionDao,
                taskInstanceDao,
                workflowInstanceDao,
                workflowExecution,
                taskInstanceContextDao);
    }

    @Override
    public String getTaskType() {
        return DependentLogicTaskChannelFactory.NAME;
    }
}
