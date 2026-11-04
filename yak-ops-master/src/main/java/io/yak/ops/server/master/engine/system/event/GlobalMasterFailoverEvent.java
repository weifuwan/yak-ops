package io.yak.ops.server.master.engine.system.event;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Date;

import lombok.Getter;

@Getter
public class GlobalMasterFailoverEvent extends AbstractSystemEvent {

    private final Date eventTime;

    public GlobalMasterFailoverEvent(Date eventTime) {
        super();
        this.eventTime = eventTime;
    }

    public static GlobalMasterFailoverEvent of(final Date eventTime) {
        checkNotNull(eventTime);
        return new GlobalMasterFailoverEvent(eventTime);
    }

    @Override
    public SystemEventType getEventType() {
        return SystemEventType.GLOBAL_MASTER_FAILOVER;
    }

    @Override
    public String toString() {
        return "GlobalMasterFailoverEvent{" +
                "eventTime=" + eventTime +
                '}';
    }
}
