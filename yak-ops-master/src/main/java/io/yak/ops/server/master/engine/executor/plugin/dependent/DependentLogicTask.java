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

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DependentLogicTask extends AbstractLogicTask<DependentParameters> {

    private static final int DEFAULT_CHECK_INTERVAL_SECONDS = 10;

    private final TaskExecutionContext taskExecutionContext;

    private final DependentTaskTracker dependentTaskTracker;

    private final long checkIntervalNanos;

    private final LongSupplier nanoTimeSupplier;

    private Long lastCheckTimeNanos;

    public DependentLogicTask(TaskExecutionContext taskExecutionContext,
                              ProjectDao projectDao,
                              WorkflowDefinitionDao workflowDefinitionDao,
                              TaskDefinitionDao taskDefinitionDao,
                              TaskInstanceDao taskInstanceDao,
                              WorkflowInstanceDao workflowInstanceDao,
                              IWorkflowExecution workflowExecution,
                              TaskInstanceContextDao taskInstanceContextDao) {
        this(taskExecutionContext, projectDao, workflowDefinitionDao, taskDefinitionDao, taskInstanceDao,
                workflowInstanceDao, workflowExecution, taskInstanceContextDao, System::nanoTime);
    }

    DependentLogicTask(TaskExecutionContext taskExecutionContext,
                       ProjectDao projectDao,
                       WorkflowDefinitionDao workflowDefinitionDao,
                       TaskDefinitionDao taskDefinitionDao,
                       TaskInstanceDao taskInstanceDao,
                       WorkflowInstanceDao workflowInstanceDao,
                       IWorkflowExecution workflowExecution,
                       TaskInstanceContextDao taskInstanceContextDao,
                       LongSupplier nanoTimeSupplier) {
        super(taskExecutionContext);
        this.taskExecutionContext = taskExecutionContext;
        this.nanoTimeSupplier = nanoTimeSupplier;
        final Integer checkInterval = taskParameters.getDependence().getCheckInterval();
        this.checkIntervalNanos = TimeUnit.SECONDS.toNanos(
                checkInterval == null ? DEFAULT_CHECK_INTERVAL_SECONDS : checkInterval);
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
        synchronized (this) {
            if (!isRunning()) {
                return taskExecutionStatus;
            }
            final long now = nanoTimeSupplier.getAsLong();
            if (lastCheckTimeNanos != null && now - lastCheckTimeNanos < checkIntervalNanos) {
                return taskExecutionStatus;
            }
            lastCheckTimeNanos = now;
        }

        final TaskExecutionStatus dependentState = dependentTaskTracker.getDependentTaskStatus();
        synchronized (this) {
            if (isRunning()) {
                taskExecutionStatus = dependentState;
            }
            return taskExecutionStatus;
        }
    }

    @Override
    public void pause() throws MasterTaskExecuteException {
        synchronized (this) {
            onTaskPaused();
        }
        log.info("Pause task : {} success", taskExecutionContext.getTaskName());
    }

    @Override
    public void kill() throws MasterTaskExecuteException {
        synchronized (this) {
            onTaskKilled();
        }
        log.info("Kill task : {} success", taskExecutionContext.getTaskName());
    }

    @Override
    public ITaskParameterDeserializer<DependentParameters> getTaskParameterDeserializer() {
        return taskParamsJson -> JSONUtils.parseObject(taskParamsJson, new TypeReference<DependentParameters>() {
        });
    }

}
