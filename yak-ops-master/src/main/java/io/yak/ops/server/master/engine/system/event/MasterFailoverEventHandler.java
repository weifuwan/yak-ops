package io.yak.ops.server.master.engine.system.event;

import io.yak.ops.server.master.failover.FailoverCoordinator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MasterFailoverEventHandler implements ISystemEventHandler<MasterFailoverEvent> {

    @Autowired
    private FailoverCoordinator failoverCoordinator;

    @Override
    public void handle(final MasterFailoverEvent masterFailoverEvent) {
        failoverCoordinator.failoverMaster(masterFailoverEvent);
    }

    @Override
    public SystemEventType matchState() {
        return SystemEventType.MASTER_FAILOVER;
    }
}
