package io.yak.ops.server.worker.registry;

import static io.yak.ops.common.constants.Constants.SLEEP_TIME_MILLIS;

import io.yak.ops.common.IStoppable;
import io.yak.ops.common.model.Server;
import io.yak.ops.common.thread.ThreadUtils;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.extract.base.utils.Host;
import io.yak.ops.meter.metrics.MetricsProvider;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.RegistryException;
import io.yak.ops.registry.api.enums.RegistryNodeType;
import io.yak.ops.server.worker.config.WorkerConfig;
import io.yak.ops.server.worker.config.WorkerServerLoadProtection;
import io.yak.ops.server.worker.executor.PhysicalTaskExecutorContainerProvider;
import io.yak.ops.server.worker.task.WorkerHeartBeatTask;

import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WorkerRegistryClient implements AutoCloseable {

    @Value("${yak-ops.startup.registry-timeout:60s}")
    private Duration startupRegistryTimeout = Duration.ofSeconds(60);

    @Autowired
    private WorkerConfig workerConfig;

    @Autowired
    private WorkerServerLoadProtection workerServerLoadProtection;

    @Autowired
    private PhysicalTaskExecutorContainerProvider physicalTaskExecutorContainerDelegator;

    @Autowired
    private RegistryClient registryClient;

    @Autowired
    private MetricsProvider metricsProvider;

    private WorkerHeartBeatTask workerHeartBeatTask;

    @PostConstruct
    public void initWorkRegistry() {
        this.workerHeartBeatTask = new WorkerHeartBeatTask(
                workerConfig,
                workerServerLoadProtection,
                metricsProvider,
                registryClient,
                physicalTaskExecutorContainerDelegator.getExecutorContainer());
    }

    public void start() {
        try {
            registry();
            registryClient.addConnectionStateListener(new WorkerConnectionStateListener(registryClient));
        } catch (Exception ex) {
            throw new RegistryException("Worker registry client start up error", ex);
        }
    }

    private void registry() {
        String workerRegistryPath = workerConfig.getWorkerRegistryPath();
        // remove before persist
        registryClient.remove(workerRegistryPath);
        registryClient.persistEphemeral(workerRegistryPath, JSONUtils.toJsonString(workerHeartBeatTask.getHeartBeat()));
        log.info("Worker node: {} registry to registry center {} successfully", workerConfig.getWorkerAddress(),
                workerRegistryPath);

        long deadline = System.nanoTime() + startupRegistryTimeout.toNanos();
        while (!registryClient.checkNodeExists(workerConfig.getWorkerAddress(), RegistryNodeType.WORKER)) {
            if (System.nanoTime() >= deadline) {
                throw new RegistryException("Timed out waiting for WORKER registration in Registry");
            }
            ThreadUtils.sleep(SLEEP_TIME_MILLIS);
        }

        workerHeartBeatTask.start();
        log.info("Worker node: {} registry finished", workerConfig.getWorkerAddress());
    }

    public Optional<Host> getAlertServerAddress() {
        List<Server> serverList = registryClient.getServerList(RegistryNodeType.ALERT_SERVER);
        if (CollectionUtils.isEmpty(serverList)) {
            return Optional.empty();
        }
        Server server = serverList.get(0);
        return Optional.of(new Host(server.getHost(), server.getPort()));
    }

    public void setRegistryStoppable(IStoppable stoppable) {
        registryClient.setStoppable(RegistryNodeType.WORKER, stoppable);
    }

    @Override
    public void close() throws IOException {
        if (workerHeartBeatTask != null) {
            workerHeartBeatTask.shutdown();
        }
        if (registryClient.isConnected()) {
            registryClient.remove(workerConfig.getWorkerRegistryPath());
        }
        log.info("Closed WorkerRegistryClient");
    }

    public boolean isAvailable() {
        return registryClient.isConnected();
    }
}
