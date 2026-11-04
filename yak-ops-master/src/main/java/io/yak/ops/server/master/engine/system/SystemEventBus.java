package io.yak.ops.server.master.engine.system;

import io.yak.ops.eventbus.AbstractDelayEventBus;
import io.yak.ops.server.master.engine.AbstractLifecycleEvent;
import io.yak.ops.server.master.engine.system.event.AbstractSystemEvent;
import io.yak.ops.server.master.engine.task.lifecycle.AbstractTaskLifecycleEvent;
import io.yak.ops.server.master.engine.workflow.lifecycle.AbstractWorkflowLifecycleLifecycleEvent;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * The event bus for system, this is used to store the whole {@link AbstractLifecycleEvent} in a workflow instance include the
 * {@link AbstractTaskLifecycleEvent} and the {@link AbstractWorkflowLifecycleLifecycleEvent}.
 */
@Slf4j
@Component
public class SystemEventBus extends AbstractDelayEventBus<AbstractSystemEvent> {

    public void publish(final AbstractSystemEvent event) {
        super.publish(event);
        log.info("Published SystemEvent: {}", event);
    }

    public AbstractSystemEvent take() throws InterruptedException {
        return delayEventQueue.take();
    }
}
