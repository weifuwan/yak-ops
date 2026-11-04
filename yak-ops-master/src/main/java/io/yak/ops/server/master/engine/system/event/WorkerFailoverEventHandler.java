package io.yak.ops.server.master.engine.system.event;

import io.yak.ops.server.master.failover.FailoverCoordinator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkerFailoverEventHandler implements ISystemEventHandler<WorkerFailoverEvent> {

    @Autowired
    private FailoverCoordinator failoverCoordinator;

    @Override
    public void handle(final WorkerFailoverEvent workerFailoverEvent) {
        failoverCoordinator.failoverWorker(workerFailoverEvent);
    }

    @Override
    public SystemEventType matchState() {
        return SystemEventType.WORKER_FAILOVER;
    }
}
