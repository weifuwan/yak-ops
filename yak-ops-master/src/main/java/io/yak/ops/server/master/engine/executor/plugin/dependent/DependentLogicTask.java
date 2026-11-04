package io.yak.ops.server.master.engine.executor.plugin.dependent;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.repository.ProjectDao;
import io.yak.ops.dao.repository.TaskDefinitionDao;
import io.yak.ops.dao.repository.TaskInstanceContextDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.plugin.task.api.parameters.DependentParameters;
import io.yak.ops.server.master.engine.executor.plugin.AbstractLogicTask;
import io.yak.ops.server.master.engine.executor.plugin.ITaskParameterDeserializer;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.exception.MasterTaskExecuteException;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DependentLogicTask extends AbstractLogicTask<DependentParameters> {

    private final TaskExecutionContext taskExecutionContext;

    private final DependentTaskTracker dependentTaskTracker;

    public DependentLogicTask(TaskExecutionContext taskExecutionContext,
                              ProjectDao projectDao,
                              WorkflowDefinitionDao workflowDefinitionDao,
                              TaskDefinitionDao taskDefinitionDao,
                              TaskInstanceDao taskInstanceDao,
                              WorkflowInstanceDao workflowInstanceDao,
                              IWorkflowExecution workflowExecution,
                              TaskInstanceContextDao taskInstanceContextDao) {
        super(taskExecutionContext);
        this.taskExecutionContext = taskExecutionContext;
        this.dependentTaskTracker = new DependentTaskTracker(
                taskExecutionContext,
                taskParameters,
                projectDao,
                workflowDefinitionDao,
                taskDefinitionDao,
                taskInstanceDao,
                workflowInstanceDao,
                taskInstanceContextDao);
        onTaskRunning();
    }

    @Override
    public void start() throws MasterTaskExecuteException {
        log.info("Dependent task: {} started", taskExecutionContext.getTaskName());
    }

    @Override
    public TaskExecutionStatus getTaskExecutionState() {
        if (isRunning()) {
            taskExecutionStatus = dependentTaskTracker.getDependentTaskStatus();
            return taskExecutionStatus;
        }
        return taskExecutionStatus;
    }

    @Override
    public void pause() throws MasterTaskExecuteException {
        onTaskPaused();
        log.info("Pause task : {} success", taskExecutionContext.getTaskName());
    }

    @Override
    public void kill() throws MasterTaskExecuteException {
        onTaskKilled();
        log.info("Kill task : {} success", taskExecutionContext.getTaskName());
    }

    @Override
    public ITaskParameterDeserializer<DependentParameters> getTaskParameterDeserializer() {
        return taskParamsJson -> JSONUtils.parseObject(taskParamsJson, new TypeReference<DependentParameters>() {
        });
    }

}
