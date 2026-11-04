package io.yak.ops.server.master.cluster.loadbalancer;

import io.yak.ops.server.master.cluster.WorkerClusters;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.common.truth.Truth;

class RandomWorkerLoadBalancerTest extends BaseWorkerLoadBalancerTest {

    @Test
    void select_defaultWorkerGroup() {
        WorkerClusters defaultWorkerClusters = getDefaultWorkerClusters();
        RandomWorkerLoadBalancer randomWorkerLoadBalancer = new RandomWorkerLoadBalancer(defaultWorkerClusters);
        // select 10000 times to check if the selected worker is in the worker list
        Map<String, Integer> workerSelectedCount = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            Optional<String> selectedWorker = randomWorkerLoadBalancer.select("default");
            Truth.assertThat(selectedWorker).isPresent();
            workerSelectedCount.put(selectedWorker.get(),
                    workerSelectedCount.getOrDefault(selectedWorker.get(), 0) + 1);
        }
        // Assert the three workers selected times are similar
        Integer times1 = workerSelectedCount.get("127.0.0.1:1234");
        Integer times2 = workerSelectedCount.get("127.0.0.2:1234");
        Integer times3 = workerSelectedCount.get("127.0.0.3:1234");
        Truth.assertThat(Math.abs(times1 - times2)).isWithin(10);
        Truth.assertThat(Math.abs(times1 - times3)).isWithin(10);
        Truth.assertThat(Math.abs(times2 - times3)).isWithin(10);
    }

    @Test
    void select_emptyWorkerGroup() {
        WorkerClusters defaultWorkerClusters = getDefaultWorkerClusters();
        RandomWorkerLoadBalancer randomWorkerLoadBalancer = new RandomWorkerLoadBalancer(defaultWorkerClusters);
        Truth.assertThat(randomWorkerLoadBalancer.select("busyCluster")).isEmpty();
    }

    @Test
    void select_workerGroupNotExist() {
        WorkerClusters defaultWorkerClusters = getDefaultWorkerClusters();
        RandomWorkerLoadBalancer randomWorkerLoadBalancer = new RandomWorkerLoadBalancer(defaultWorkerClusters);
        Truth.assertThat(randomWorkerLoadBalancer.select("notExistCluster")).isEmpty();
    }

}
