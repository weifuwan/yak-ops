package io.yak.ops.task.executor.events;

import io.yak.ops.eventbus.AbstractDelayEvent;

import lombok.Builder;
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
public abstract class AbstractTaskExecutorLifecycleEvent extends AbstractDelayEvent
        implements
            ITaskExecutorLifecycleEvent {

    protected int taskInstanceId;

    @Builder.Default
    protected long eventCreateTime = System.currentTimeMillis();

    protected TaskExecutorLifecycleEventType type;

}
