package io.yak.ops.server.master.engine;

import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

/**
 * The event handler interface, used to handle the event
 */
public interface ILifecycleEventHandler<T extends AbstractLifecycleEvent> {

    void handle(final IWorkflowExecution workflowExecution,
                final T event);

    ILifecycleEventType matchEventType();

}
