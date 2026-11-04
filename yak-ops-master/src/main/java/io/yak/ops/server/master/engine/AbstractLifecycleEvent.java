package io.yak.ops.server.master.engine;

import io.yak.ops.eventbus.AbstractDelayEvent;

public abstract class AbstractLifecycleEvent extends AbstractDelayEvent {

    public AbstractLifecycleEvent() {
        super();
    }

    public AbstractLifecycleEvent(long delayTime) {
        super(delayTime);
    }

    public abstract ILifecycleEventType getEventType();
}
