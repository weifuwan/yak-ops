package io.yak.ops.server.master.engine.system.event;

import io.yak.ops.eventbus.AbstractDelayEvent;

import java.util.Date;

public abstract class AbstractSystemEvent extends AbstractDelayEvent {

    public AbstractSystemEvent() {
        super();
    }

    public AbstractSystemEvent(long delayTime) {
        super(delayTime);
    }

    /**
     * The event happen time.
     */
    public abstract Date getEventTime();

    /**
     * The event type.
     */
    public abstract SystemEventType getEventType();

}
