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
@SuperBuilder
@NoArgsConstructor
public class TaskExecutorFinalizeLifecycleEvent extends AbstractTaskExecutorLifecycleEvent {

    public static TaskExecutorFinalizeLifecycleEvent of(final ITaskExecutor taskExecutor) {
        return TaskExecutorFinalizeLifecycleEvent.builder()
                .taskInstanceId(taskExecutor.getId())
                .type(TaskExecutorLifecycleEventType.FINALIZE)
                .build();
    }

}
