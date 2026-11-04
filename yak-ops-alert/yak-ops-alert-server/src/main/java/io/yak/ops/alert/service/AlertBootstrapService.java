package io.yak.ops.alert.service;

import io.yak.ops.alert.registry.AlertRegistryClient;
import io.yak.ops.alert.rpc.AlertRpcServer;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * The bootstrap service for alert server. it will start all the necessary component for alert server.
 */
@Slf4j
@Service
public final class AlertBootstrapService implements AutoCloseable {

    private final AlertEventFetcher alertEventFetcher;

    private final AlertEventLoop alertEventLoop;

    public AlertBootstrapService(AlertRpcServer alertRpcServer,
                                 AlertRegistryClient alertRegistryClient,
                                 AlertHAServer alertHAServer,
                                 AlertEventFetcher alertEventFetcher,
                                 AlertEventLoop alertEventLoop) {
        this.alertEventFetcher = alertEventFetcher;
        this.alertEventLoop = alertEventLoop;
    }

    public void start() {
        log.info("AlertBootstrapService starting...");
        alertEventFetcher.start();
        alertEventLoop.start();
        log.info("AlertBootstrapService started...");
    }

    @Override
    public void close() {
        log.info("AlertBootstrapService stopping...");
        alertEventFetcher.shutdown();
        alertEventLoop.shutdown();
        log.info("AlertBootstrapService stopped...");
    }
}
