package io.yak.ops.api.metrics;

import io.yak.ops.registry.api.RegistryClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class ApiHealthIndicator implements HealthIndicator {

    @Autowired
    private RegistryClient registryClient;

    @Override
    public Health health() {
        try {
            if (registryClient.isConnected()) {
                return Health.up().build();
            }
            return Health.down().build();
        } catch (Exception ex) {
            return Health.down().withException(ex).build();
        }
    }

}
