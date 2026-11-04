package io.yak.ops.task.executor.events;

import io.yak.ops.task.executor.ITaskExecutor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class TaskExecutorPauseLifecycleEvent extends AbstractTaskExecutorLifecycleEvent
        implements
            IOperableTaskExecutorLifecycleEvent {

    public static TaskExecutorPauseLifecycleEvent of(final ITaskExecutor taskExecutor) {
        return TaskExecutorPauseLifecycleEvent.builder()
                .taskInstanceId(taskExecutor.getId())
                .eventCreateTime(System.currentTimeMillis())
                .type(TaskExecutorLifecycleEventType.PAUSE)
                .build();
    }
}
