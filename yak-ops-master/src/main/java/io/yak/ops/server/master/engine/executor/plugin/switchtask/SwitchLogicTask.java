package io.yak.ops.server.master.engine.executor.plugin.switchtask;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.plugin.task.api.model.SwitchResultVo;
import io.yak.ops.plugin.task.api.parameters.SwitchParameters;
import io.yak.ops.plugin.task.api.utils.VarPoolUtils;
import io.yak.ops.server.master.engine.executor.plugin.AbstractLogicTask;
import io.yak.ops.server.master.engine.executor.plugin.ITaskParameterDeserializer;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.runner.IWorkflowExecuteContext;
import io.yak.ops.server.master.utils.SwitchTaskUtils;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SwitchLogicTask extends AbstractLogicTask<SwitchParameters> {

    private final IWorkflowExecution workflowExecution;
    private final TaskInstance taskInstance;

    public SwitchLogicTask(IWorkflowExecution workflowExecution,
                           TaskExecutionContext taskExecutionContext) {
        super(taskExecutionContext);
        this.workflowExecution = workflowExecution;
        this.taskInstance = workflowExecution
                .getWorkflowExecuteContext()
                .getWorkflowExecutionGraph()
                .getTaskExecutionById(taskExecutionContext.getTaskInstanceId())
                .getTaskInstance();
        onTaskRunning();
    }

    @Override
    public void start() {
        if (CollectionUtils.isEmpty(taskParameters.getSwitchResult().getDependTaskList())) {
            // If the branch is empty then will go into the default branch
            // This case shouldn't happen, we can directly throw exception and forbid the user to set branch
            log.info("The switch items is empty");
            moveToDefaultBranch();
        } else {
            calculateSwitchBranch();
        }
        checkIfBranchExist(taskParameters.getNextBranch());
        taskInstance.setTaskParams(JSONUtils.toJsonString(taskParameters));

        onTaskSuccess();
        log.info("Switch task execute finished");
    }

    private void moveToDefaultBranch() {
        log.info("Begin to move to the default branch");
        if (taskParameters.getSwitchResult().getNextNode() == null) {
            throw new IllegalArgumentException(
                    "The default branch is empty, please check the switch task configuration");
        }
        taskParameters.setNextBranch(taskParameters.getSwitchResult().getNextNode());
        log.info("The condition is not satisfied, move to the default branch: {}",
                getTaskName(taskParameters.getNextBranch()));
    }

    private void calculateSwitchBranch() {
        List<SwitchResultVo> switchResultVos = taskParameters.getSwitchResult().getDependTaskList();
        Map<String, Property> globalParams = taskExecutionContext.getPrepareParamsMap();
        Map<String, Property> varParams = VarPoolUtils.deserializeVarPool(taskInstance.getVarPool())
                .stream()
                .collect(Collectors.toMap(Property::getProp, Property -> Property));

        Long nextBranch = null;
        for (SwitchResultVo switchResultVo : switchResultVos) {
            log.info("Begin to execute switch item: {} ", switchResultVo);
            try {
                String content = SwitchTaskUtils.generateContentWithTaskParams(switchResultVo.getCondition(),
                        globalParams, varParams);
                log.info("Format condition sentence::{} successfully", content);
                boolean conditionResult = SwitchTaskUtils.evaluate(content);
                log.info("Execute condition sentence: {} successfully: {}", content, conditionResult);
                if (conditionResult) {
                    // If matched, break the loop
                    nextBranch = switchResultVo.getNextNode();
                    break;
                }
            } catch (Exception e) {
                log.info("Execute switch item: {} failed", switchResultVo, e);
            }
        }

        if (nextBranch == null) {
            log.info("All switch item is not satisfied");
            moveToDefaultBranch();
        } else {
            log.info("The condition is satisfied, move to the next branch: {}", getTaskName(nextBranch));
            taskParameters.setNextBranch(nextBranch);
        }
    }

    private void checkIfBranchExist(Long branchNode) {
        if (branchNode == null) {
            throw new IllegalArgumentException("The branch is empty, please check the switch task configuration");
        }
        if (workflowExecution.getWorkflowExecuteContext().getWorkflowGraph()
                .getTaskNodeByCode(branchNode) == null) {
            throw new IllegalArgumentException(
                    "The branch(code= " + branchNode
                            + ") is not in the dag, please check the switch task configuration");
        }
    }

    private String getTaskName(Long taskCode) {
        return Optional.ofNullable(workflowExecution.getWorkflowExecuteContext())
                .map(IWorkflowExecuteContext::getWorkflowGraph)
                .map(iWorkflowGraph -> iWorkflowGraph.getTaskNodeByCode(taskCode))
                .map(TaskDefinition::getName)
                .orElse(null);
    }

    @Override
    public void pause() {
        log.info("The SwitchTask does not support pause operation");
    }

    @Override
    public void kill() {
        log.info("The SwitchTask does not support kill operation");
    }

    @Override
    public ITaskParameterDeserializer<SwitchParameters> getTaskParameterDeserializer() {
        return taskParamsJson -> JSONUtils.parseObject(taskParamsJson, new TypeReference<SwitchParameters>() {
        });
    }

}
