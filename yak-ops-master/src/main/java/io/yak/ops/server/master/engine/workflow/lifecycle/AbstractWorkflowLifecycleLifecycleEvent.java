package io.yak.ops.server.master.engine.workflow.lifecycle;

import io.yak.ops.server.master.engine.AbstractLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;

public abstract class AbstractWorkflowLifecycleLifecycleEvent extends AbstractLifecycleEvent {

    public AbstractWorkflowLifecycleLifecycleEvent() {
        super(0L);
    }

    public AbstractWorkflowLifecycleLifecycleEvent(long delayTime) {
        super(delayTime);
    }

    public abstract IWorkflowExecution getWorkflowExecution();

}
