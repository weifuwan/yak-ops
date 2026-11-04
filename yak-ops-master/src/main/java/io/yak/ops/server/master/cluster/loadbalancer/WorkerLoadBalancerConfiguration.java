package io.yak.ops.server.master.cluster.loadbalancer;

import io.yak.ops.server.master.cluster.ClusterManager;
import io.yak.ops.server.master.config.MasterConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkerLoadBalancerConfiguration {

    @Bean
    public IWorkerLoadBalancer randomWorkerLoadBalancer(MasterConfig masterConfig, ClusterManager clusterManager) {
        WorkerLoadBalancerConfigurationProperties workerLoadBalancerConfigurationProperties =
                masterConfig.getWorkerLoadBalancerConfigurationProperties();
        switch (workerLoadBalancerConfigurationProperties.getType()) {
            case RANDOM:
                return new RandomWorkerLoadBalancer(clusterManager.getWorkerClusters());
            case ROUND_ROBIN:
                return new RoundRobinWorkerLoadBalancer(clusterManager.getWorkerClusters());
            case FIXED_WEIGHTED_ROUND_ROBIN:
                return new FixedWeightedRoundRobinWorkerLoadBalancer(clusterManager.getWorkerClusters());
            case DYNAMIC_WEIGHTED_ROUND_ROBIN:
                return new DynamicWeightedRoundRobinWorkerLoadBalancer(
                        clusterManager.getWorkerClusters(),
                        workerLoadBalancerConfigurationProperties.getDynamicWeightConfigProperties());
            default:
                throw new IllegalArgumentException(
                        "unSupport worker load balancer type " + workerLoadBalancerConfigurationProperties.getType());
        }
    }

}
