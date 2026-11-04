package io.yak.ops.alert.registry;

import io.yak.ops.alert.config.AlertConfig;
import io.yak.ops.alert.service.AlertHAServer;
import io.yak.ops.meter.metrics.MetricsProvider;
import io.yak.ops.registry.api.RegistryClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AlertRegistryClient implements AutoCloseable {

    @Autowired
    private RegistryClient registryClient;

    @Autowired
    private AlertConfig alertConfig;

    @Autowired
    private MetricsProvider metricsProvider;

    private AlertHeartbeatTask alertHeartbeatTask;

    @Autowired
    private AlertHAServer alertHAServer;

    public void start() {
        log.info("AlertRegistryClient starting...");
        alertHeartbeatTask = new AlertHeartbeatTask(alertConfig, metricsProvider, registryClient, alertHAServer);
        alertHeartbeatTask.start();
        // start heartbeat task
        log.info("AlertRegistryClient started...");
    }

    @Override
    public void close() {
        log.info("AlertRegistryClient closing...");
        alertHeartbeatTask.shutdown();
        log.info("AlertRegistryClient closed...");
    }

    public boolean isAvailable() {
        return registryClient.isConnected();
    }
}
