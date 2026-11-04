package io.yak.ops.server.master.engine.task.lifecycle.event;

import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TaskSuccessLifecycleEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    private final Date endTime;

    private final List<Property> varPool;

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.SUCCEEDED;
    }

    @Override
    public String toString() {
        return "TaskSuccessLifecycleEvent{" +
                "task=" + taskExecution.getName() +
                ", endTime=" + endTime +
                ", varPool='" + varPool + '\'' +
                '}';
    }
}
