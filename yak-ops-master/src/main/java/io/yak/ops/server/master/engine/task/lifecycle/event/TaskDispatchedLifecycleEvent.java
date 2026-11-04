package io.yak.ops.server.master.engine.task.lifecycle.event;

import io.yak.ops.server.master.engine.ILifecycleEventType;
import io.yak.ops.server.master.engine.task.execution.ITaskExecution;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.task.lifecycle.TaskLifecycleEventType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TaskDispatchedLifecycleEvent extends AbstractTaskLifecycleEvent {

    private ITaskExecution taskExecution;

    private String executorHost;

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.DISPATCHED;
    }

    @Override
    public String toString() {
        return "TaskDispatchedLifecycleEvent{" +
                "task=" + taskExecution.getName() +
                ", executorHost='" + executorHost + '\'' +
                '}';
    }
}
