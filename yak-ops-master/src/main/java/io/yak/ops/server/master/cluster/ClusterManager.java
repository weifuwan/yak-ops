package io.yak.ops.server.master.cluster;

import io.yak.ops.common.model.MasterHeartBeat;
import io.yak.ops.common.model.WorkerHeartBeat;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.enums.RegistryNodeType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ClusterManager {

    @Getter
    private MasterClusters masterClusters;

    @Getter
    private WorkerClusters workerClusters;

    @Autowired
    private MasterSlotManager masterSlotManager;

    @Autowired
    private WorkerGroupChangeNotifier workerGroupChangeNotifier;

    @Autowired
    private RegistryClient registryClient;

    public ClusterManager() {
        this.masterClusters = new MasterClusters();
        this.workerClusters = new WorkerClusters();
    }

    public void start() {
        initializeMasterClusters();
        initializeWorkerClusters();
        log.info("ClusterManager started...");
    }

    /**
     * Initialize the master clusters.
     * <p> 1. Register master slot listener once master clusters changed.
     * <p> 2. Fetch master nodes from registry.
     * <p> 3. Subscribe the master change event.
     */
    private void initializeMasterClusters() {
        this.masterClusters.registerListener(new MasterSlotChangeListenerAdaptor(masterSlotManager, masterClusters));

        registryClient.getServerList(RegistryNodeType.MASTER).forEach(server -> {
            final MasterHeartBeat masterHeartBeat =
                    JSONUtils.parseObject(server.getHeartBeatInfo(), MasterHeartBeat.class);
            masterClusters.onServerAdded(MasterServerMetadata.parseFromHeartBeat(masterHeartBeat));
        });
        log.info("Initialized MasterClusters: {}", JSONUtils.toPrettyJsonString(masterClusters.getServers()));

        this.registryClient.subscribe(RegistryNodeType.MASTER.getRegistryPath(), masterClusters);
    }

    /**
     * Initialize the worker clusters.
     * <p> 1. Fetch worker nodes from registry.
     * <p> 2. Register worker group change notifier once worker clusters changed.
     * <p> 3. Subscribe the worker change event.
     */
    private void initializeWorkerClusters() {
        registryClient.getServerList(RegistryNodeType.WORKER).forEach(server -> {
            final WorkerHeartBeat workerHeartBeat =
                    JSONUtils.parseObject(server.getHeartBeatInfo(), WorkerHeartBeat.class);
            workerClusters.onServerAdded(WorkerServerMetadata.parseFromHeartBeat(workerHeartBeat));
        });
        log.info("Initialized WorkerClusters: {}", JSONUtils.toPrettyJsonString(workerClusters.getServers()));

        this.registryClient.subscribe(RegistryNodeType.WORKER.getRegistryPath(), workerClusters);

        this.workerGroupChangeNotifier.subscribeWorkerGroupsChange(workerClusters);
        this.workerGroupChangeNotifier.start();
    }

}
