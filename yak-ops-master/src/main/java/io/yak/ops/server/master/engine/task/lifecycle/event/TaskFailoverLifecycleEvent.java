package io.yak.ops.server.master.engine.task.lifecycle.event;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TaskFailoverLifecycleEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    public static TaskFailoverLifecycleEvent of(final ITaskExecution taskExecuteRunnable) {
        return new TaskFailoverLifecycleEvent(taskExecuteRunnable);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.FAILOVER;
    }

    @Override
    public String toString() {
        return "TaskFailoverLifecycleEvent{" +
                "task=" + taskExecution.getName() +
                '}';
    }
}
