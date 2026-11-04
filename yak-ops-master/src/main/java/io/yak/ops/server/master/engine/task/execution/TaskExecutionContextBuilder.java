package io.yak.ops.server.master.engine.task.execution;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.common.enums.TimeoutFlag;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.enums.TaskTimeoutStrategy;
import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.plugin.task.api.parameters.resource.ResourceParametersHelper;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskExecutionContextBuilder {

    public static TaskExecutionContextBuilder get() {
        return new TaskExecutionContextBuilder();
    }

    private final TaskExecutionContext taskExecutionContext;

    public TaskExecutionContextBuilder() {
        this.taskExecutionContext = new TaskExecutionContext();
    }

    /**
     * build taskInstance related info
     *
     * @param taskInstance taskInstance
     * @return TaskExecutionContextBuilder
     */
    public TaskExecutionContextBuilder buildTaskInstanceRelatedInfo(final TaskInstance taskInstance) {
        taskExecutionContext.setTaskInstanceId(taskInstance.getId());
        taskExecutionContext.setTaskName(taskInstance.getName());
        taskExecutionContext.setFirstSubmitTime(DateUtils.dateToTimeStamp(taskInstance.getFirstSubmitTime()));
        taskExecutionContext.setStartTime(DateUtils.dateToTimeStamp(taskInstance.getStartTime()));
        taskExecutionContext.setTaskType(taskInstance.getTaskType());
        taskExecutionContext.setLogPath(taskInstance.getLogPath());
        taskExecutionContext.setWorkerGroup(taskInstance.getWorkerGroup());
        taskExecutionContext.setHost(taskInstance.getHost());
        taskExecutionContext.setDryRun(taskInstance.getDryRun());
        taskExecutionContext.setCpuQuota(taskInstance.getCpuQuota());
        taskExecutionContext.setMemoryMax(taskInstance.getMemoryMax());
        taskExecutionContext.setAppIds(taskInstance.getAppLink());
        return this;
    }

    public TaskExecutionContextBuilder buildTaskDefinitionRelatedInfo(final TaskDefinition taskDefinition) {
        // todo: remove the timeout setting here the timeout strategy should be used at master
        taskExecutionContext.setTaskTimeout(Integer.MAX_VALUE);
        if (taskDefinition.getTimeoutFlag() == TimeoutFlag.OPEN) {
            taskExecutionContext.setTaskTimeoutStrategy(taskDefinition.getTimeoutNotifyStrategy());
            if (taskDefinition.getTimeoutNotifyStrategy() == TaskTimeoutStrategy.FAILED
                    || taskDefinition.getTimeoutNotifyStrategy() == TaskTimeoutStrategy.WARNFAILED) {
                taskExecutionContext.setTaskTimeout(
                        (int) Math.min(TimeUnit.MINUTES.toSeconds(taskDefinition.getTimeout()), Integer.MAX_VALUE));
            }
        }
        taskExecutionContext.setTaskParams(taskDefinition.getTaskParams());
        return this;
    }

    /**
     * build processInstance related info
     *
     * @param workflowInstance processInstance
     * @return TaskExecutionContextBuilder
     */
    public TaskExecutionContextBuilder buildProcessInstanceRelatedInfo(final WorkflowInstance workflowInstance) {
        taskExecutionContext.setWorkflowInstanceId(workflowInstance.getId());
        taskExecutionContext.setWorkflowInstanceName(workflowInstance.getName());
        taskExecutionContext.setProjectCode(workflowInstance.getProjectCode());
        taskExecutionContext.setScheduleTime(DateUtils.dateToTimeStamp(workflowInstance.getScheduleTime()));
        taskExecutionContext.setGlobalParams(workflowInstance.getGlobalParams());
        taskExecutionContext.setExecutorId(workflowInstance.getExecutorId());
        taskExecutionContext.setTenantCode(workflowInstance.getTenantCode());
        taskExecutionContext.setWorkflowDefinitionCode(workflowInstance.getWorkflowDefinitionCode());
        taskExecutionContext.setWorkflowDefinitionVersion(workflowInstance.getWorkflowDefinitionVersion());
        return this;
    }

    public TaskExecutionContextBuilder buildResourceParameters(final ResourceParametersHelper parametersHelper) {
        taskExecutionContext.setResourceParametersHelper(parametersHelper);
        return this;
    }

    /**
     * The runtime params, include local params from task, global params from workflow, startup params from command, varpool params from pre-task, built-in params from system
     *
     */
    public TaskExecutionContextBuilder buildPrepareParams(final Map<String, Property> propertyMap) {
        taskExecutionContext.setPrepareParamsMap(propertyMap);
        return this;
    }

    public TaskExecutionContextBuilder buildWorkflowInstanceHost(final String masterHost) {
        taskExecutionContext.setWorkflowInstanceHost(masterHost);
        return this;
    }

    public TaskExecutionContextBuilder buildEnvironmentConfig(final String environmentConfig) {
        taskExecutionContext.setEnvironmentConfig(environmentConfig);
        return this;
    }

    /**
     * create
     *
     * @return taskExecutionContext
     */

    public TaskExecutionContext create() {
        checkNotNull(taskExecutionContext.getWorkflowInstanceHost(), "The workflow instance host cannot be empty");
        return taskExecutionContext;
    }

}
