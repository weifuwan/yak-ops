package io.yak.ops.server.master.cluster.loadbalancer;

import io.yak.ops.server.master.cluster.WorkerClusters;

import org.apache.commons.collections4.CollectionUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

/**
 * The worker load balancer used to select a worker from the {@link WorkerClusters} by random algorithm.
 */
public class RandomWorkerLoadBalancer implements IWorkerLoadBalancer {

    private final WorkerClusters workerClusters;

    private final SecureRandom secureRandom;

    public RandomWorkerLoadBalancer(WorkerClusters workerClusters) {
        this.workerClusters = workerClusters;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public Optional<String> select(@NotNull String workerGroup) {
        List<String> workerServerAddresses = workerClusters.getNormalWorkerServerAddressByGroup(workerGroup);
        if (CollectionUtils.isEmpty(workerServerAddresses)) {
            return Optional.empty();
        }
        int index = secureRandom.nextInt(workerServerAddresses.size());
        return Optional.of(workerServerAddresses.get(index));
    }

    @Override
    public WorkerLoadBalancerType getType() {
        return WorkerLoadBalancerType.RANDOM;
    }
}
