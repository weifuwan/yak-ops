package io.yak.ops.server.master.engine.task.lifecycle;

import io.yak.ops.server.master.engine.AbstractLifecycleEvent;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;

public abstract class AbstractTaskLifecycleEvent extends AbstractLifecycleEvent {

    public AbstractTaskLifecycleEvent() {
        super();
    }

    public AbstractTaskLifecycleEvent(long delayTime) {
        super(delayTime);
    }

    public abstract ITaskExecution getTaskExecution();

}
