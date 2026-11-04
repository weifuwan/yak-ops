package io.yak.ops.server.master.cluster;

import io.yak.ops.server.master.engine.system.SystemEventBus;
import io.yak.ops.server.master.engine.system.event.MasterFailoverEvent;
import io.yak.ops.server.master.engine.system.event.WorkerFailoverEvent;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ClusterStateMonitors {

    @Autowired
    private ClusterManager clusterManager;

    @Autowired
    private SystemEventBus systemEventBus;

    public void start() {
        clusterManager.getMasterClusters()
                .registerListener((IClusters.ServerRemovedListener<MasterServerMetadata>) this::masterRemoved);
        clusterManager.getWorkerClusters()
                .registerListener((IClusters.ServerRemovedListener<WorkerServerMetadata>) this::workerRemoved);
        log.info("ClusterStateMonitors started...");
    }

    void masterRemoved(final MasterServerMetadata masterServer) {
        // We set a delay of 30 seconds for the master failover event
        // If the master can reconnect to registry within 30 seconds, the master will skip failover.
        systemEventBus.publish(MasterFailoverEvent.of(masterServer, new Date(), 30_000));
    }

    void workerRemoved(final WorkerServerMetadata workerServer) {
        // We set a delay of 30 seconds for the worker failover event
        // If the worker can reconnect to registry within 30 seconds, the worker will skip failover.
        systemEventBus.publish(WorkerFailoverEvent.of(workerServer, new Date(), 30_000));
    }

}
