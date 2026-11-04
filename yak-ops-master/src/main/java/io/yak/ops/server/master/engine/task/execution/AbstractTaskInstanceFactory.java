package io.yak.ops.server.master.engine.task.execution;

import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.utils.EnvironmentUtils;
import io.yak.ops.dao.utils.WorkerGroupUtils;

public abstract class AbstractTaskInstanceFactory<BUILDER extends ITaskInstanceFactory.ITaskInstanceBuilder>
        implements
            ITaskInstanceFactory<BUILDER> {

    protected TaskInstance cloneTaskInstance(TaskInstance originTaskInstance) {
        final TaskInstance result = new TaskInstance();
        result.setId(originTaskInstance.getId());
        result.setName(originTaskInstance.getName());
        result.setTaskType(originTaskInstance.getTaskType());
        result.setWorkflowInstanceId(originTaskInstance.getWorkflowInstanceId());
        result.setWorkflowInstanceName(originTaskInstance.getWorkflowInstanceName());
        result.setProjectCode(originTaskInstance.getProjectCode());
        result.setTaskCode(originTaskInstance.getTaskCode());
        result.setTaskDefinitionVersion(originTaskInstance.getTaskDefinitionVersion());
        result.setState(originTaskInstance.getState());
        result.setFirstSubmitTime(originTaskInstance.getFirstSubmitTime());
        result.setSubmitTime(originTaskInstance.getSubmitTime());
        result.setStartTime(originTaskInstance.getStartTime());
        result.setEndTime(originTaskInstance.getEndTime());
        result.setHost(originTaskInstance.getHost());
        result.setExecutePath(originTaskInstance.getExecutePath());
        result.setLogPath(originTaskInstance.getLogPath());
        result.setRetryTimes(originTaskInstance.getRetryTimes());
        result.setAlertFlag(originTaskInstance.getAlertFlag());
        result.setPid(originTaskInstance.getPid());
        result.setAppLink(originTaskInstance.getAppLink());
        result.setFlag(originTaskInstance.getFlag());
        result.setMaxRetryTimes(originTaskInstance.getMaxRetryTimes());
        result.setRetryInterval(originTaskInstance.getRetryInterval());
        result.setTaskInstancePriority(originTaskInstance.getTaskInstancePriority());
        result.setWorkerGroup(originTaskInstance.getWorkerGroup());
        result.setEnvironmentCode(originTaskInstance.getEnvironmentCode());
        result.setExecutorId(originTaskInstance.getExecutorId());
        result.setVarPool(originTaskInstance.getVarPool());
        result.setExecutorName(originTaskInstance.getExecutorName());
        result.setDelayTime(originTaskInstance.getDelayTime());
        result.setTaskParams(originTaskInstance.getTaskParams());
        result.setDryRun(originTaskInstance.getDryRun());
        result.setTaskGroupId(originTaskInstance.getTaskGroupId());
        result.setCpuQuota(originTaskInstance.getCpuQuota());
        result.setMemoryMax(originTaskInstance.getMemoryMax());
        result.setTaskExecuteType(originTaskInstance.getTaskExecuteType());
        return result;
    }

    protected void injectMetadataFromTaskDefinition(TaskInstance taskInstance, TaskDefinition taskDefinition) {
        taskInstance.setName(taskDefinition.getName());
        taskInstance.setTaskType(taskDefinition.getTaskType());
        taskInstance.setTaskCode(taskDefinition.getCode());
        taskInstance.setTaskDefinitionVersion(taskDefinition.getVersion());
        taskInstance.setMaxRetryTimes(taskDefinition.getFailRetryTimes());
        taskInstance.setRetryInterval(taskDefinition.getFailRetryInterval());
        taskInstance.setTaskInstancePriority(taskDefinition.getTaskPriority());
        taskInstance.setWorkerGroup(
                WorkerGroupUtils.getWorkerGroupOrDefault(
                        taskInstance.getWorkerGroup(), taskDefinition.getWorkerGroup()));
        taskInstance.setEnvironmentCode(
                EnvironmentUtils.getEnvironmentCodeOrDefault(
                        taskInstance.getEnvironmentCode(), taskDefinition.getEnvironmentCode()));
        taskInstance.setDelayTime(taskDefinition.getDelayTime());
        taskInstance.setTaskParams(taskDefinition.getTaskParams());
        taskInstance.setTaskGroupId(taskDefinition.getTaskGroupId());
        taskInstance.setCpuQuota(taskDefinition.getCpuQuota());
        taskInstance.setMemoryMax(taskDefinition.getMemoryMax());
        taskInstance.setTaskExecuteType(taskDefinition.getTaskExecuteType());
    }

    protected void injectMetadataFromWorkflowInstance(TaskInstance taskInstance, WorkflowInstance workflowInstance) {
        taskInstance.setWorkflowInstanceId(workflowInstance.getId());
        taskInstance.setWorkflowInstanceName(workflowInstance.getName());
        taskInstance.setProjectCode(workflowInstance.getProjectCode());
        taskInstance.setWorkerGroup(
                WorkerGroupUtils.getWorkerGroupOrDefault(
                        taskInstance.getWorkerGroup(), workflowInstance.getWorkerGroup()));
        taskInstance.setEnvironmentCode(
                EnvironmentUtils.getEnvironmentCodeOrDefault(
                        taskInstance.getEnvironmentCode(), workflowInstance.getEnvironmentCode()));
        taskInstance.setExecutorId(workflowInstance.getExecutorId());
        taskInstance.setVarPool(workflowInstance.getVarPool());
        taskInstance.setExecutorName(workflowInstance.getExecutorName());
        taskInstance.setDryRun(workflowInstance.getDryRun());
    }

}
