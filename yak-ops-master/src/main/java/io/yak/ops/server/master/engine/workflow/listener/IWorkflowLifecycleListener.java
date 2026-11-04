package io.yak.ops.server.master.engine.workflow.listener;

import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.AbstractWorkflowLifecycleLifecycleEvent;

public interface IWorkflowLifecycleListener {

    void notifyWorkflowLifecycleEvent(final IWorkflowExecution workflowExecution,
                                      final AbstractWorkflowLifecycleLifecycleEvent workflowLifecycleLifecycleEvent);

    boolean match(AbstractWorkflowLifecycleLifecycleEvent event);

}
