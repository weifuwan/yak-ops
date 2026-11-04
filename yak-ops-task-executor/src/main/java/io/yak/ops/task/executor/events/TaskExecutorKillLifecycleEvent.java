package io.yak.ops.task.executor.events;

import io.yak.ops.task.executor.ITaskExecutor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class TaskExecutorKillLifecycleEvent extends AbstractTaskExecutorLifecycleEvent
        implements
            IOperableTaskExecutorLifecycleEvent {

    public static TaskExecutorKillLifecycleEvent of(final ITaskExecutor taskExecutor) {
        return TaskExecutorKillLifecycleEvent.builder()
                .taskInstanceId(taskExecutor.getId())
                .type(TaskExecutorLifecycleEventType.KILL)
                .build();
    }

}
