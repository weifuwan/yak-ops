package io.yak.ops.server.master.metrics;

import io.yak.ops.server.master.registry.MasterRegistryClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class MasterHealthIndicator implements HealthIndicator {

    @Autowired
    private MasterRegistryClient masterRegistryClient;

    @Override
    public Health health() {
        try {
            if (masterRegistryClient.isAvailable()) {
                return Health.up().build();
            }
            return Health.down().build();
        } catch (Exception ex) {
            return Health.down().withException(ex).build();
        }
    }
}
