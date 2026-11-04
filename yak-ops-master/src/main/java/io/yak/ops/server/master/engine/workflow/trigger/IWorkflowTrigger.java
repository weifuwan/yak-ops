package io.yak.ops.server.master.engine.workflow.trigger;

/**
 * The trigger interface of the workflow, used to trigger the workflow and generate the workflow instance.
 *
 * @param <TriggerRequest>  trigger request
 * @param <TriggerResponse> trigger response
 */
public interface IWorkflowTrigger<TriggerRequest, TriggerResponse> {

    TriggerResponse triggerWorkflow(final TriggerRequest triggerRequest);

}
