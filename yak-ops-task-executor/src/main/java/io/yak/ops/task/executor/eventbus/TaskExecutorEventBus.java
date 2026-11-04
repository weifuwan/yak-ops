package io.yak.ops.task.executor.eventbus;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.eventbus.AbstractDelayEventBus;
import io.yak.ops.plugin.task.api.log.TaskLogMarkers;
import io.yak.ops.task.executor.events.AbstractTaskExecutorLifecycleEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskExecutorEventBus extends AbstractDelayEventBus<AbstractTaskExecutorLifecycleEvent> {

    public void publish(final AbstractTaskExecutorLifecycleEvent event) {
        super.publish(event);
        log.info(TaskLogMarkers.excludeInTaskLog(), "Publish {}: {}", event.getClass().getSimpleName(),
                JSONUtils.toPrettyJsonString(event));
    }

}
