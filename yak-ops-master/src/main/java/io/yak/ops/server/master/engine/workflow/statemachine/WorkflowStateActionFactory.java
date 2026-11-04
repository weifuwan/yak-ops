package io.yak.ops.server.master.engine.workflow.statemachine;

import io.yak.ops.common.enums.WorkflowExecutionStatus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class WorkflowStateActionFactory {

    private final Map<WorkflowExecutionStatus, IWorkflowStateAction> workflowStateActionMap = new HashMap<>();

    public WorkflowStateActionFactory(List<IWorkflowStateAction> workflowStateActions) {
        workflowStateActions.forEach(
                workflowStateAction -> workflowStateActionMap.put(workflowStateAction.matchState(),
                        workflowStateAction));
        Arrays.stream(WorkflowExecutionStatus.values()).forEach(this::getAction);
    }

    public IWorkflowStateAction getAction(final WorkflowExecutionStatus workflowExecutionStatus) {
        final IWorkflowStateAction workflowStateAction = workflowStateActionMap.get(workflowExecutionStatus);
        if (workflowStateAction == null) {
            throw new IllegalArgumentException("Cannot find WorkflowStateAction for state: " + workflowExecutionStatus);
        }
        return workflowStateAction;
    }

}
