package io.yak.ops.server.master.engine.task.lifecycle.event;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class TaskKilledLifecycleEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    private final Date endTime;

    public static TaskKilledLifecycleEvent of(final ITaskExecution taskExecution) {
        return new TaskKilledLifecycleEvent(taskExecution, new Date());
    }

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.KILLED;
    }

    @Override
    public String toString() {
        return "TaskKilledLifecycleEvent{" +
                "task" + taskExecution.getName() +
                ", endTime=" + endTime +
                '}';
    }
}
