package io.yak.ops.server.master.engine.executor.plugin.subworkflow;

import static io.yak.ops.plugin.task.api.utils.VarPoolUtils.deserializeVarPool;
import static java.util.Arrays.asList;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.entity.WorkflowInstanceRelation;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.dao.repository.WorkflowInstanceMapDao;
import io.yak.ops.extract.master.command.ICommandParam;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstancePauseRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstancePauseResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverFailureTasksRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverSuspendTasksRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceStopRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceStopResponse;
import io.yak.ops.extract.master.transportor.workflow.WorkflowManualTriggerRequest;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.plugin.task.api.parameters.SubWorkflowParameters;
import io.yak.ops.server.master.engine.executor.plugin.AbstractLogicTask;
import io.yak.ops.server.master.engine.executor.plugin.ITaskParameterDeserializer;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.exception.MasterTaskExecuteException;
import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.events.TaskExecutorRuntimeContextChangedLifecycleEvent;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubWorkflowLogicTask extends AbstractLogicTask<SubWorkflowParameters> {

    private SubWorkflowLogicTaskRuntimeContext subWorkflowLogicTaskRuntimeContext;

    private final IWorkflowExecution workflowExecution;

    private final ApplicationContext applicationContext;

    private SubWorkflowTracker subWorkflowTracker;

    private ITaskExecutor taskExecutor;

    public SubWorkflowLogicTask(final TaskExecutionContext taskExecutionContext,
                                final IWorkflowExecution workflowExecution,
                                final ITaskExecutor taskExecutor,
                                final ApplicationContext applicationContext) {
        super(taskExecutionContext);
        this.taskExecutor = taskExecutor;
        this.workflowExecution = workflowExecution;
        this.applicationContext = applicationContext;
        this.subWorkflowLogicTaskRuntimeContext = JSONUtils.parseObject(
                taskExecutionContext.getAppIds(),
                SubWorkflowLogicTaskRuntimeContext.class);
        onTaskRunning();
    }

    @Override
    public void start() throws MasterTaskExecuteException {
        subWorkflowLogicTaskRuntimeContext = initializeSubWorkflowInstance();
        upsertSubWorkflowRelation();
        taskExecutionContext.setAppIds(JSONUtils.toJsonString(subWorkflowLogicTaskRuntimeContext));

        taskExecutor.getTaskExecutorEventBus()
                .publish(TaskExecutorRuntimeContextChangedLifecycleEvent.of(taskExecutor));

        subWorkflowTracker = new SubWorkflowTracker(
                subWorkflowLogicTaskRuntimeContext,
                applicationContext.getBean(WorkflowInstanceDao.class));
    }

    @Override
    public TaskExecutionStatus getTaskExecutionState() {
        if (subWorkflowTracker == null) {
            // The sub workflow has not been started
            return taskExecutionStatus;
        }
        taskExecutionStatus = subWorkflowTracker.getSubWorkflowState();
        return taskExecutionStatus;
    }

    @Override
    public void pause() throws MasterTaskExecuteException {
        if (subWorkflowLogicTaskRuntimeContext == null) {
            log.info("subWorkflowLogicTaskRuntimeContext is null cannot pause");
            return;
        }
        final Integer subWorkflowInstanceId = subWorkflowLogicTaskRuntimeContext.getSubWorkflowInstanceId();
        final WorkflowInstancePauseResponse pauseResponse = applicationContext
                .getBean(SubWorkflowControlClient.class)
                .pauseWorkflowInstance(new WorkflowInstancePauseRequest(subWorkflowInstanceId));
        if (pauseResponse.isSuccess()) {
            log.info("Pause sub workflowInstance: id={}", subWorkflowInstanceId + " success");
        } else {
            log.info("Pause sub workflowInstance: id={} failed with response: {}", subWorkflowInstanceId,
                    pauseResponse);
        }
    }

    @Override
    public void kill() throws MasterTaskExecuteException {
        if (subWorkflowLogicTaskRuntimeContext == null) {
            log.info("subWorkflowLogicTaskRuntimeContext is null cannot kill");
            return;
        }
        final Integer subWorkflowInstanceId = subWorkflowLogicTaskRuntimeContext.getSubWorkflowInstanceId();
        final WorkflowInstanceStopResponse stopResponse = applicationContext
                .getBean(SubWorkflowControlClient.class)
                .stopWorkflowInstance(new WorkflowInstanceStopRequest(subWorkflowInstanceId));
        if (stopResponse.isSuccess()) {
            log.info("Kill sub workflowInstance: id={}", subWorkflowInstanceId + " success");
        } else {
            log.info("Kill sub workflowInstance: id={} failed with response: {}", subWorkflowInstanceId, stopResponse);
        }
    }

    @Override
    public ITaskParameterDeserializer<SubWorkflowParameters> getTaskParameterDeserializer() {
        return taskParamsJson -> JSONUtils.parseObject(taskParamsJson, new TypeReference<SubWorkflowParameters>() {
        });
    }

    private SubWorkflowLogicTaskRuntimeContext initializeSubWorkflowInstance() {
        // todo: doFailover if the runtime context is not null and task is generated by failover

        if (subWorkflowLogicTaskRuntimeContext == null) {
            return triggerNewSubWorkflow();
        }

        // In some cases, workflow instance's command type has not been changed,
        // there should better to use command.type instead
        switch (workflowExecution.getWorkflowExecuteContext().getCommand().getCommandType()) {
            case RECOVER_TOLERANCE_FAULT_PROCESS:
                return recoverFromFaultTolerantTasks();
            case RECOVER_SUSPENDED_PROCESS:
                return recoverFromSuspendTasks();
            case START_FAILURE_TASK_PROCESS:
                return recoverFromFailedTasks();
            default:
                return triggerNewSubWorkflow();
        }
    }

    private SubWorkflowLogicTaskRuntimeContext recoverFromFaultTolerantTasks() {
        final WorkflowInstanceDao workflowInstanceDao = applicationContext.getBean(WorkflowInstanceDao.class);
        final WorkflowInstance subWorkflowInstance = workflowInstanceDao.queryById(
                subWorkflowLogicTaskRuntimeContext.getSubWorkflowInstanceId());

        if (subWorkflowInstance != null) {
            // If the sub workflow instance is existed, means we already trigger the sub workflow instance.
            // So we don't need to trigger again.
            return subWorkflowLogicTaskRuntimeContext;
        }

        // The sub-workflow's state is bad, trigger a new sub-workflow instance
        return triggerNewSubWorkflow();
    }

    private SubWorkflowLogicTaskRuntimeContext recoverFromFailedTasks() {
        final SubWorkflowControlClient subWorkflowControlClient =
                applicationContext.getBean(SubWorkflowControlClient.class);
        if (subWorkflowLogicTaskRuntimeContext == null) {
            log.info("The task: {} triggerType is FAILED_RECOVER but runtimeContext is null will trigger again",
                    taskExecutionContext.getTaskName());
            return triggerNewSubWorkflow();
        }
        final WorkflowInstanceRecoverFailureTasksRequest recoverFailureTasksRequest =
                WorkflowInstanceRecoverFailureTasksRequest.builder()
                        .workflowInstanceId(subWorkflowLogicTaskRuntimeContext.getSubWorkflowInstanceId())
                        .userId(taskExecutionContext.getExecutorId())
                        .build();
        subWorkflowControlClient.triggerFromFailureTasks(recoverFailureTasksRequest);
        return subWorkflowLogicTaskRuntimeContext;
    }

    private SubWorkflowLogicTaskRuntimeContext recoverFromSuspendTasks() {
        final SubWorkflowControlClient subWorkflowControlClient =
                applicationContext.getBean(SubWorkflowControlClient.class);
        if (subWorkflowLogicTaskRuntimeContext == null) {
            log.info("The task: {} is recover from suspend but runtimeContext is null will trigger again",
                    taskExecutionContext.getTaskName());
            return triggerNewSubWorkflow();
        }
        final WorkflowInstanceRecoverSuspendTasksRequest recoverSuspendTasksRequest =
                WorkflowInstanceRecoverSuspendTasksRequest.builder()
                        .workflowInstanceId(subWorkflowLogicTaskRuntimeContext.getSubWorkflowInstanceId())
                        .userId(taskExecutionContext.getExecutorId())
                        .build();
        subWorkflowControlClient.triggerFromSuspendTasks(recoverSuspendTasksRequest);
        return subWorkflowLogicTaskRuntimeContext;
    }

    private SubWorkflowLogicTaskRuntimeContext triggerNewSubWorkflow() {
        final WorkflowInstance workflowInstance = workflowExecution.getWorkflowInstance();

        final WorkflowDefinition subWorkflowDefinition = applicationContext.getBean(WorkflowDefinitionDao.class)
                .queryByCode(taskParameters.getWorkflowDefinitionCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot find the sub workflow definition: " + taskParameters.getWorkflowDefinitionCode()));

        final ICommandParam commandParam =
                JSONUtils.parseObject(workflowInstance.getCommandParam(), ICommandParam.class);

        final List<Property> paramList = mergeParams(asList(
                new ArrayList<>(deserializeVarPool(workflowInstance.getGlobalParams())),
                commandParam.getCommandParams(),
                new ArrayList<>(deserializeVarPool(workflowInstance.getVarPool()))));

        final WorkflowManualTriggerRequest workflowManualTriggerRequest = WorkflowManualTriggerRequest.builder()
                .userId(taskExecutionContext.getExecutorId())
                .workflowDefinitionCode(subWorkflowDefinition.getCode())
                .workflowDefinitionVersion(subWorkflowDefinition.getVersion())
                .failureStrategy(workflowInstance.getFailureStrategy())
                .warningType(workflowInstance.getWarningType())
                .warningGroupId(workflowInstance.getWarningGroupId())
                .workflowInstancePriority(workflowInstance.getWorkflowInstancePriority())
                .workerGroup(workflowInstance.getWorkerGroup())
                .tenantCode(workflowInstance.getTenantCode())
                .environmentCode(workflowInstance.getEnvironmentCode())
                .scheduleTime(workflowInstance.getScheduleTime())
                .timeZone(commandParam.getTimeZone())
                .startParamList(paramList)
                .dryRun(Flag.of(workflowInstance.getDryRun()))
                .build();
        final Integer subWorkflowInstanceId = applicationContext
                .getBean(SubWorkflowControlClient.class)
                .triggerSubWorkflow(workflowManualTriggerRequest);
        return SubWorkflowLogicTaskRuntimeContext.of(subWorkflowInstanceId);
    }

    private List<Property> mergeParams(List<List<Property>> params) {
        if (CollectionUtils.isEmpty(params)) {
            return Collections.emptyList();
        }
        if (params.size() == 1) {
            return params.get(0);
        }
        Map<String, Property> result = new HashMap<>();
        for (List<Property> param : params) {
            if (CollectionUtils.isEmpty(param)) {
                continue;
            }
            for (Property property : param) {
                result.put(property.getProp(), property);
            }
        }
        return new ArrayList<>(result.values());
    }

    private void upsertSubWorkflowRelation() {
        final WorkflowInstanceMapDao workflowInstanceMapDao = applicationContext.getBean(WorkflowInstanceMapDao.class);
        WorkflowInstanceRelation workflowInstanceRelation = workflowInstanceMapDao.queryWorkflowMapByParent(
                taskExecutionContext.getWorkflowInstanceId(),
                taskExecutionContext.getTaskInstanceId());
        if (workflowInstanceRelation == null) {
            workflowInstanceRelation = WorkflowInstanceRelation.builder()
                    .parentWorkflowInstanceId(taskExecutionContext.getWorkflowInstanceId())
                    .parentTaskInstanceId(taskExecutionContext.getTaskInstanceId())
                    .workflowInstanceId(subWorkflowLogicTaskRuntimeContext.getSubWorkflowInstanceId())
                    .build();
            workflowInstanceMapDao.insert(workflowInstanceRelation);
        } else {
            workflowInstanceRelation
                    .setWorkflowInstanceId(subWorkflowLogicTaskRuntimeContext.getSubWorkflowInstanceId());
            workflowInstanceMapDao.updateById(workflowInstanceRelation);
        }
    }
}
