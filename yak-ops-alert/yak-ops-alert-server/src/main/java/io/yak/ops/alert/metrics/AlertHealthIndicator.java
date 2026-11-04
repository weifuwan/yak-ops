package io.yak.ops.alert.metrics;

import io.yak.ops.alert.registry.AlertRegistryClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class AlertHealthIndicator implements HealthIndicator {

    @Autowired
    private AlertRegistryClient alertRegistryClient;

    @Override
    public Health health() {
        try {
            if (alertRegistryClient.isAvailable()) {
                return Health.up().build();
            }
            return Health.down().build();
        } catch (Exception ex) {
            return Health.down().withException(ex).build();
        }
    }
}
