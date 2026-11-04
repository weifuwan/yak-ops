package io.yak.ops.server.master.engine.system.event;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.server.master.cluster.MasterServerMetadata;

import java.util.Date;

import lombok.Getter;

@Getter
public class MasterFailoverEvent extends AbstractSystemEvent {

    private final MasterServerMetadata masterServerMetadata;
    // The time when the event occurred. This might be different at different nodes.
    private final Date eventTime;

    private MasterFailoverEvent(final MasterServerMetadata masterServerMetadata,
                                final Date eventTime,
                                final long delayTime) {
        super(delayTime);
        this.masterServerMetadata = masterServerMetadata;
        this.eventTime = eventTime;
    }

    public static MasterFailoverEvent of(final MasterServerMetadata masterServerMetadata,
                                         final Date eventTime,
                                         final long delayTime) {
        checkNotNull(masterServerMetadata);
        checkNotNull(eventTime);
        return new MasterFailoverEvent(masterServerMetadata, eventTime, delayTime);
    }

    @Override
    public SystemEventType getEventType() {
        return SystemEventType.MASTER_FAILOVER;
    }

    @Override
    public String toString() {
        return "MasterFailoverEvent{" +
                "masterServerMetadata='" + masterServerMetadata + '\'' +
                ", eventTime=" + eventTime +
                ", delayTime=" + delayTime +
                '}';
    }
}
