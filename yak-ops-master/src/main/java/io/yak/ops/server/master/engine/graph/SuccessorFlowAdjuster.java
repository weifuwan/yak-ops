package io.yak.ops.server.master.engine.graph;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.model.SwitchResultVo;
import io.yak.ops.plugin.task.api.parameters.ConditionsParameters;
import io.yak.ops.plugin.task.api.parameters.SwitchParameters;
import io.yak.ops.plugin.task.api.utils.TaskTypeUtils;
import io.yak.ops.server.master.engine.executor.plugin.condition.ConditionLogicTask;
import io.yak.ops.server.master.engine.executor.plugin.switchtask.SwitchLogicTask;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Used to adjust the flow of tasks.
 * <p> Only {@link ConditionLogicTask} and {@link SwitchLogicTask} need to adjust the flow of tasks.
 */
@Slf4j
@Component
public class SuccessorFlowAdjuster {

    /**
     * Control the post flow of task.
     * <p> If the task is forbidden, then will not adjust the successor.
     * <p> If the task is skipped, then will try to mark successors which predecessors are all skipped as skipped.
     * <p> If the task {@link ConditionLogicTask}, then will adjust the flow according to the condition result.
     * <p> If the task {@link SwitchLogicTask}, then will adjust the flow according to the switch result.
     */
    public void adjustSuccessorFlow(final ITaskExecution taskExecution) {
        final IWorkflowExecutionGraph workflowExecutionGraph = taskExecution.getWorkflowExecutionGraph();

        if (workflowExecutionGraph.isTaskExecutionSkipped(taskExecution)) {
            // If the successor flow's all parent is skipped, then mark the successor skipped.
            for (ITaskExecution successor : workflowExecutionGraph.getSuccessors(taskExecution)) {
                if (workflowExecutionGraph.isAllPredecessorsSkipped(successor)) {
                    workflowExecutionGraph.markTaskSkipped(successor);
                }
            }
            return;
        }

        if (workflowExecutionGraph.isTaskExecutionForbidden(taskExecution)) {
            return;
        }

        final String taskType = taskExecution.getTaskInstance().getTaskType();
        if (TaskTypeUtils.isConditionTask(taskType)) {
            adjustConditionTaskSuccessorFlow(taskExecution);
            return;
        }

        if (TaskTypeUtils.isSwitchTask(taskType)) {
            adjustSwitchTaskSuccessorFlow(taskExecution);
            return;
        }
    }

    private void adjustConditionTaskSuccessorFlow(final ITaskExecution taskExecution) {
        final String taskParams = taskExecution.getTaskInstance().getTaskParams();
        final ConditionsParameters conditionsParameters = JSONUtils.parseObject(taskParams, ConditionsParameters.class);
        if (conditionsParameters == null) {
            throw new IllegalArgumentException("Condition task params: " + taskParams + " is invalid.");
        }
        final ConditionsParameters.ConditionResult conditionResult = conditionsParameters.getConditionResult();
        if (conditionResult == null) {
            throw new IllegalArgumentException("ConditionResult: is null in taskParam: " + taskParams);
        }
        final List<Long> needSkippedBranch;
        if (conditionResult.isConditionSuccess()) {
            needSkippedBranch = conditionResult.getFailedNode();
        } else {
            needSkippedBranch = conditionResult.getSuccessNode();
        }
        markTaskSkipped(taskExecution, needSkippedBranch);
    }

    private void adjustSwitchTaskSuccessorFlow(final ITaskExecution taskExecution) {
        final String taskParams = taskExecution.getTaskInstance().getTaskParams();
        final SwitchParameters switchParameters = JSONUtils.parseObject(taskParams, SwitchParameters.class);
        if (switchParameters == null) {
            throw new IllegalArgumentException("Switch task params: " + taskParams + " is invalid.");
        }
        final SwitchParameters.SwitchResult switchResult = switchParameters.getSwitchResult();
        if (switchResult == null) {
            throw new IllegalArgumentException("ConditionResult: is null in taskParam: " + taskParams);
        }
        final Set<Long> needSkippedBranch = new HashSet<>();
        if (switchResult.getNextNode() != null) {
            needSkippedBranch.add(switchResult.getNextNode());
        }
        if (CollectionUtils.isNotEmpty(switchResult.getDependTaskList())) {
            for (SwitchResultVo switchResultVo : switchResult.getDependTaskList()) {
                needSkippedBranch.add(switchResultVo.getNextNode());
            }
        }
        needSkippedBranch.remove(switchParameters.getNextBranch());
        markTaskSkipped(taskExecution, needSkippedBranch);
    }

    private void markTaskSkipped(final ITaskExecution taskExecution,
                                 final Collection<Long> needSkippedTaskCodes) {
        if (CollectionUtils.isEmpty(needSkippedTaskCodes)) {
            return;
        }
        final IWorkflowExecutionGraph workflowExecutionGraph = taskExecution.getWorkflowExecutionGraph();
        for (Long taskCode : needSkippedTaskCodes) {
            final ITaskExecution branch = workflowExecutionGraph.getTaskExecutionByTaskCode(taskCode);
            if (branch == null) {
                log.info("Branch(taskCode={}) is not found in the workflow: {}.", taskCode,
                        taskExecution.getWorkflowInstance().getName());
                continue;
            }
            workflowExecutionGraph.markTaskSkipped(branch);
        }
    }

}
