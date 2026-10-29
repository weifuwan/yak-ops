package io.yak.ops.worker.task.executor.eventbus;

import io.yak.ops.worker.task.executor.eventbus.base.AbstractDelayEventBus;
import io.yak.ops.worker.task.executor.events.AbstractTaskExecutorLifecycleEvent;

/** DolphinScheduler TaskExecutorEventBus: a delayed, typed lifecycle event queue. */
public class TaskExecutorEventBus extends AbstractDelayEventBus<AbstractTaskExecutorLifecycleEvent> {
    @Override
    public void publish(final AbstractTaskExecutorLifecycleEvent event) {
        super.publish(event);
    }
}
