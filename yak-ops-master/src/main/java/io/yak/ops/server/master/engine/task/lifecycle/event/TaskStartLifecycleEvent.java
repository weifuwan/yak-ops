package io.yak.ops.server.master.engine.task.lifecycle.event;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TaskStartLifecycleEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    public static TaskStartLifecycleEvent of(ITaskExecution taskExecution) {
        return new TaskStartLifecycleEvent(taskExecution);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.START;
    }

    @Override
    public String toString() {
        return "TaskStartLifecycleEvent{" +
                "task=" + taskExecution.getName() +
                '}';
    }
}
