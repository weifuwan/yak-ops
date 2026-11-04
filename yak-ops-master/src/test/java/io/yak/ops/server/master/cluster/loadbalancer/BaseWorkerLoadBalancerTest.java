package io.yak.ops.server.master.cluster.loadbalancer;

import io.yak.ops.common.enums.ServerStatus;
import io.yak.ops.dao.entity.WorkerGroup;
import io.yak.ops.server.master.cluster.WorkerClusters;
import io.yak.ops.server.master.cluster.WorkerServerMetadata;

import java.util.List;

import com.google.common.collect.Lists;

public class BaseWorkerLoadBalancerTest {

    /**
     * "default" -> 127.0.0.1:1234, 127.0.0.2:1234, 127.0.0.3:1234, 127.0.0.4:1234
     * "sparkCluster" -> 127.0.0.1:1234
     * "flinkCluster" ->
     * "busyCluster" -> 127.0.0.4:1234
     */
    protected WorkerClusters getDefaultWorkerClusters() {
        List<WorkerGroup> workerGroups = Lists.newArrayList(
                WorkerGroup.builder()
                        .name("sparkCluster")
                        .addrList("127.0.0.1:1234")
                        .build(),
                WorkerGroup.builder()
                        .name("flinkCluster")
                        .build(),
                WorkerGroup.builder()
                        .name("busyCluster")
                        .build());

        List<WorkerServerMetadata> workerServers = Lists.newArrayList(
                WorkerServerMetadata.builder()
                        .address("127.0.0.1:1234")
                        .serverStatus(ServerStatus.NORMAL)
                        .cpuUsage(0.1)
                        .memoryUsage(0.1)
                        .build(),
                WorkerServerMetadata.builder()
                        .address("127.0.0.2:1234")
                        .serverStatus(ServerStatus.NORMAL)
                        .cpuUsage(0.2)
                        .memoryUsage(0.2)
                        .build(),
                WorkerServerMetadata.builder()
                        .address("127.0.0.3:1234")
                        .serverStatus(ServerStatus.NORMAL)
                        .cpuUsage(0.3)
                        .memoryUsage(0.3)
                        .build(),
                WorkerServerMetadata.builder()
                        .address("127.0.0.4:1234")
                        .serverStatus(ServerStatus.BUSY)
                        .cpuUsage(0.8)
                        .memoryUsage(0.8)
                        .build());

        WorkerClusters workerClusters = new WorkerClusters();
        workerClusters.onWorkerGroupAdd(workerGroups);
        workerServers.forEach(workerClusters::onServerAdded);
        return workerClusters;
    }

}
