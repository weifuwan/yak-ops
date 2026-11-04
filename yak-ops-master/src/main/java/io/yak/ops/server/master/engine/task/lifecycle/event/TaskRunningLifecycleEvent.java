package io.yak.ops.server.master.engine.task.lifecycle.event;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TaskRunningLifecycleEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    private final String logPath;

    private final Date startTime;

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.RUNNING;
    }

    @Override
    public String toString() {
        return "TaskRunningLifecycleEvent{" +
                "task=" + taskExecution.getName() +
                ", logPath='" + logPath + '\'' +
                ", startTime=" + startTime +
                '}';
    }
}
