package io.yak.ops.server.master.engine.system.event;

import io.yak.ops.server.master.failover.FailoverCoordinator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GlobalMasterFailoverEventHandler implements ISystemEventHandler<GlobalMasterFailoverEvent> {

    @Autowired
    private FailoverCoordinator failoverCoordinator;

    @Override
    public void handle(final GlobalMasterFailoverEvent systemEvent) {
        failoverCoordinator.globalMasterFailover(systemEvent);
    }

    @Override
    public SystemEventType matchState() {
        return SystemEventType.GLOBAL_MASTER_FAILOVER;
    }
}
