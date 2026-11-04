package io.yak.ops.server.master.engine.task.lifecycle.event;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TaskDispatchLifecycleEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    public static TaskDispatchLifecycleEvent of(final ITaskExecution taskExecution) {
        return new TaskDispatchLifecycleEvent(taskExecution);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.DISPATCH;
    }

    @Override
    public String toString() {
        return "TaskDispatchLifecycleEvent{" +
                "task=" + taskExecution.getName() +
                '}';
    }
}
