package io.yak.ops.server.master.cluster.loadbalancer;

public enum WorkerLoadBalancerType {
    RANDOM,
    ROUND_ROBIN,
    FIXED_WEIGHTED_ROUND_ROBIN,
    DYNAMIC_WEIGHTED_ROUND_ROBIN,
}
