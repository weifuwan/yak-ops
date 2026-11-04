package io.yak.ops.server.master.engine.system.event;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.server.master.cluster.WorkerServerMetadata;

import java.util.Date;

import lombok.Getter;

@Getter
public class WorkerFailoverEvent extends AbstractSystemEvent {

    private final WorkerServerMetadata workerServerMetadata;
    private final Date eventTime;

    private WorkerFailoverEvent(final WorkerServerMetadata workerServerMetadata,
                                final Date eventTime,
                                final long delayTime) {
        super(delayTime);
        this.workerServerMetadata = workerServerMetadata;
        this.eventTime = eventTime;
    }

    public static WorkerFailoverEvent of(final WorkerServerMetadata workerServerMetadata,
                                         final Date eventTime,
                                         final long delayTime) {
        checkNotNull(workerServerMetadata);
        checkNotNull(eventTime);
        return new WorkerFailoverEvent(workerServerMetadata, eventTime, delayTime);
    }

    @Override
    public SystemEventType getEventType() {
        return SystemEventType.WORKER_FAILOVER;
    }

    @Override
    public String toString() {
        return "WorkerFailoverEvent{" +
                "workerServerMetadata='" + workerServerMetadata + '\'' +
                ", eventTime=" + eventTime +
                ", delayTime=" + delayTime +
                '}';
    }
}
