package io.yak.ops.server.master.cluster.loadbalancer;

import io.yak.ops.server.master.cluster.WorkerClusters;

import java.util.Optional;

import lombok.NonNull;

/**
 * The worker load balancer used to select a worker from the {@link WorkerClusters} by load balancer algorithm.
 */
public interface IWorkerLoadBalancer {

    /**
     * Select a worker address under the given worker group.
     *
     * @param workerGroup worker group cannot be null.
     * @return the selected worker address, or empty if no worker is available.
     */
    Optional<String> select(@NonNull String workerGroup);

    WorkerLoadBalancerType getType();

}
