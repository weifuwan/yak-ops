package io.yak.ops.server.master.registry;

import static io.yak.ops.common.constants.Constants.SLEEP_TIME_MILLIS;

import io.yak.ops.common.IStoppable;
import io.yak.ops.common.thread.ThreadUtils;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.common.utils.NetUtils;
import io.yak.ops.meter.metrics.MetricsProvider;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.RegistryException;
import io.yak.ops.registry.api.enums.RegistryNodeType;
import io.yak.ops.server.master.config.MasterConfig;
import io.yak.ops.server.master.config.MasterServerLoadProtection;
import io.yak.ops.server.master.engine.MasterCoordinator;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>YakOps master register client, used to connect to registry and hand the registry events.
 * <p>When the Master node startup, it will register in registry center. And start a {@link MasterHeartBeatTask} to update its metadata in registry.
 */
@Component
@Slf4j
public class MasterRegistryClient implements AutoCloseable {

    @Value("${yak-ops.startup.registry-timeout:60s}")
    private Duration startupRegistryTimeout = Duration.ofSeconds(60);

    @Autowired
    private RegistryClient registryClient;

    @Autowired
    private MasterConfig masterConfig;

    @Autowired
    private MasterServerLoadProtection masterServerLoadProtection;

    @Autowired
    private MetricsProvider metricsProvider;

    @Autowired
    private MasterCoordinator masterCoordinator;

    private MasterHeartBeatTask masterHeartBeatTask;

    public void start() {
        try {
            this.masterHeartBeatTask = new MasterHeartBeatTask(masterConfig, masterServerLoadProtection,
                    metricsProvider, registryClient, masterCoordinator);
            // master registry
            registry();
            registryClient.addConnectionStateListener(new MasterConnectionStateListener(registryClient));
        } catch (Exception e) {
            throw new RegistryException("Master registry client start up error", e);
        }
    }

    public void setRegistryStoppable(IStoppable stoppable) {
        registryClient.setStoppable(RegistryNodeType.MASTER, stoppable);
    }

    @Override
    public void close() {
        // TODO unsubscribe MasterRegistryDataListener
        if (masterHeartBeatTask != null) {
            masterHeartBeatTask.shutdown();
        }
        if (registryClient.isConnected()) {
            deregister();
        }
        log.info("Closed MasterRegistryClient");
    }

    /**
     * Registry the current master server itself to registry.
     */
    void registry() {
        log.info("Master node : {} registering to registry center", masterConfig.getMasterAddress());
        String masterRegistryPath = masterConfig.getMasterRegistryPath();

        // remove before persist
        registryClient.remove(masterRegistryPath);
        registryClient.persistEphemeral(masterRegistryPath, JSONUtils.toJsonString(masterHeartBeatTask.getHeartBeat()));

        long deadline = System.nanoTime() + startupRegistryTimeout.toNanos();
        while (!registryClient.checkNodeExists(NetUtils.getHost(), RegistryNodeType.MASTER)) {
            if (System.nanoTime() >= deadline) {
                throw new RegistryException("Timed out waiting for MASTER registration in Registry");
            }
            ThreadUtils.sleep(SLEEP_TIME_MILLIS);
        }

        masterHeartBeatTask.start();
        log.info("Master node : {} registered to registry center successfully", masterConfig.getMasterAddress());

    }

    public void deregister() {
        try {
            registryClient.remove(masterConfig.getMasterRegistryPath());
            log.info("Master node : {} unRegistry to register center.", masterConfig.getMasterAddress());
            if (masterHeartBeatTask != null) {
                masterHeartBeatTask.shutdown();
            }
        } catch (Exception e) {
            log.error("MasterServer remove registry path exception ", e);
        }
    }

    public boolean isAvailable() {
        return registryClient.isConnected();
    }
}
