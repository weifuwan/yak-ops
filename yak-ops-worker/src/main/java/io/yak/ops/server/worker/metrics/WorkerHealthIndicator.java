package io.yak.ops.server.worker.metrics;

import io.yak.ops.server.worker.registry.WorkerRegistryClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class WorkerHealthIndicator implements HealthIndicator {

    @Autowired
    private WorkerRegistryClient workerRegistryClient;

    @Override
    public Health health() {
        try {
            if (workerRegistryClient.isAvailable()) {
                return Health.up().build();
            }
            return Health.down().build();
        } catch (Exception ex) {
            return Health.down().withException(ex).build();
        }
    }
}
