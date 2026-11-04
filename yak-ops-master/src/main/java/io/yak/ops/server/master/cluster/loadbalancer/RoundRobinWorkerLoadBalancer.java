package io.yak.ops.server.master.cluster.loadbalancer;

import io.yak.ops.server.master.cluster.WorkerClusters;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.NotNull;

/**
 * The worker load balancer used to select a worker from the {@link WorkerClusters} by round-robin algorithm.
 */
public class RoundRobinWorkerLoadBalancer implements IWorkerLoadBalancer {

    private final WorkerClusters workerClusters;

    private final AtomicInteger robinIndex = new AtomicInteger(0);

    public RoundRobinWorkerLoadBalancer(WorkerClusters workerClusters) {
        this.workerClusters = workerClusters;
    }

    @Override
    public Optional<String> select(@NotNull String workerGroup) {
        List<String> workerServerAddresses = workerClusters.getNormalWorkerServerAddressByGroup(workerGroup);
        if (CollectionUtils.isEmpty(workerServerAddresses)) {
            return Optional.empty();
        }
        return Optional.of(workerServerAddresses.get(robinIndex.getAndIncrement() % workerServerAddresses.size()));
    }

    @Override
    public WorkerLoadBalancerType getType() {
        return WorkerLoadBalancerType.ROUND_ROBIN;
    }
}
