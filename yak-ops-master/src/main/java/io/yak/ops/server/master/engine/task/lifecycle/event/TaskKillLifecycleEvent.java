package io.yak.ops.server.master.engine.task.lifecycle.event;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;

import lombok.Getter;

@Getter
public class TaskKillLifecycleEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    private TaskKillLifecycleEvent(ITaskExecution taskExecution, long delayTime) {
        super(delayTime);
        this.taskExecution = taskExecution;
    }

    public static TaskKillLifecycleEvent of(final ITaskExecution taskExecution) {
        return of(taskExecution, 0);
    }

    public static TaskKillLifecycleEvent of(final ITaskExecution taskExecution, long delayTime) {
        return new TaskKillLifecycleEvent(taskExecution, delayTime);
    }

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.KILL;
    }

    @Override
    public String toString() {
        return "TaskKillLifecycleEvent{" +
                "task=" + taskExecution.getName() + ", " +
                "delayTime=" + delayTime +
                '}';
    }
}
