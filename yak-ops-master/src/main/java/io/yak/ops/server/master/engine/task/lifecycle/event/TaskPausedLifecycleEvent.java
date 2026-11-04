package io.yak.ops.server.master.engine.task.lifecycle.event;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TaskPausedLifecycleEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    public static TaskPausedLifecycleEvent of(final ITaskExecution taskExecution) {
        return new TaskPausedLifecycleEvent(taskExecution);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.PAUSED;
    }

    @Override
    public String toString() {
        return "TaskPausedLifecycleEvent{" +
                "task=" + taskExecution.getName() +
                '}';
    }
}
