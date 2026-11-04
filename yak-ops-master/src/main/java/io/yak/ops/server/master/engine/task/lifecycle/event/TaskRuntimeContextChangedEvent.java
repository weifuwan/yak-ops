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
public class TaskRuntimeContextChangedEvent extends AbstractTaskLifecycleEvent {

    private final ITaskExecution taskExecution;

    private final String runtimeContext;

    @Override
    public ILifecycleEventType getEventType() {
        return TaskLifecycleEventType.RUNTIME_CONTEXT_CHANGED;
    }

    @Override
    public String toString() {
        return "TaskRunningLifecycleEvent{" +
                "task=" + taskExecution.getName() +
                ", runtimeContext=" + runtimeContext +
                '}';
    }
}
