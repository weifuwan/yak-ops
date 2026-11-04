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
public class TaskFatalLifecycleEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    private final Date endTime;

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.FATAL;
    }

    @Override
    public String toString() {
        return "TaskFatalLifecycleEvent{" +
                "task=" + taskExecution.getName() +
                ", endTime=" + endTime +
                '}';
    }
}
