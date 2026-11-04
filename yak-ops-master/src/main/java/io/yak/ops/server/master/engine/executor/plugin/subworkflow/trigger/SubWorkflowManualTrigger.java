package io.yak.ops.server.master.engine.executor.plugin.subworkflow.trigger;

import io.yak.ops.common.enums.Flag;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.extract.master.transportor.workflow.WorkflowManualTriggerRequest;
import io.yak.ops.server.master.engine.workflow.trigger.WorkflowManualTrigger;

import org.apache.commons.lang3.tuple.ImmutablePair;

import org.springframework.stereotype.Component;

/**
 * Manual trigger of the workflow, used to trigger the workflow and generate the workflow instance in the manual way.
 */
@Component
public class SubWorkflowManualTrigger extends WorkflowManualTrigger {

    @Override
    protected ImmutablePair<WorkflowDefinition, WorkflowInstance> constructWorkflowInstance(final WorkflowManualTriggerRequest workflowManualTriggerRequest) {
        final ImmutablePair<WorkflowDefinition, WorkflowInstance> pair =
                super.constructWorkflowInstance(workflowManualTriggerRequest);
        pair.getRight().setIsSubWorkflow(Flag.YES);
        return pair;
    }

}
