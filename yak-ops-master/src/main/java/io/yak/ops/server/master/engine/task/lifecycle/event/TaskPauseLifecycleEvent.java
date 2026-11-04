package io.yak.ops.server.master.engine.task.lifecycle.event;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;

import lombok.Getter;

@Getter
public class TaskPauseLifecycleEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    private TaskPauseLifecycleEvent(final ITaskExecution taskExecution, long delayTime) {
        super(delayTime);
        this.taskExecution = taskExecution;
    }

    public static TaskPauseLifecycleEvent of(ITaskExecution taskExecution) {
        return of(taskExecution, 0);
    }

    public static TaskPauseLifecycleEvent of(ITaskExecution taskExecution, long delayTime) {
        return new TaskPauseLifecycleEvent(taskExecution, delayTime);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.PAUSE;
    }

    @Override
    public String toString() {
        return "TaskPauseLifecycleEvent{" +
                "task=" + taskExecution.getName() + ", " +
                "delayTime=" + delayTime +
                '}';
    }
}
