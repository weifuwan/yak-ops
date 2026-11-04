package io.yak.ops.server.master.registry;

import io.yak.ops.common.enums.ServerStatus;
import io.yak.ops.common.lifecycle.ServerLifeCycleManager;
import io.yak.ops.common.model.BaseHeartBeatTask;
import io.yak.ops.common.model.MasterHeartBeat;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.common.utils.NetUtils;
import io.yak.ops.common.utils.OSUtils;
import io.yak.ops.meter.metrics.MetricsProvider;
import io.yak.ops.meter.metrics.SystemMetrics;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.enums.RegistryNodeType;
import io.yak.ops.registry.api.utils.RegistryUtils;
import io.yak.ops.server.master.config.MasterConfig;
import io.yak.ops.server.master.config.MasterServerLoadProtection;
import io.yak.ops.server.master.engine.MasterCoordinator;
import io.yak.ops.server.master.metrics.MasterServerMetrics;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MasterHeartBeatTask extends BaseHeartBeatTask<MasterHeartBeat> {

    private final MasterConfig masterConfig;

    private MasterServerLoadProtection masterServerLoadProtection;

    private final MetricsProvider metricsProvider;

    private final RegistryClient registryClient;

    private final MasterCoordinator masterCoordinator;

    private final String heartBeatPath;

    private final int processId;

    public MasterHeartBeatTask(@NonNull MasterConfig masterConfig,
                               @NonNull MasterServerLoadProtection masterServerLoadProtection,
                               @NonNull MetricsProvider metricsProvider,
                               @NonNull RegistryClient registryClient,
                               @NonNull MasterCoordinator masterCoordinator) {
        super("MasterHeartBeatTask", masterConfig.getMaxHeartbeatInterval().toMillis());
        this.masterConfig = masterConfig;
        this.masterServerLoadProtection = masterServerLoadProtection;
        this.metricsProvider = metricsProvider;
        this.registryClient = registryClient;
        this.masterCoordinator = masterCoordinator;
        this.heartBeatPath = masterConfig.getMasterRegistryPath();
        this.processId = OSUtils.getProcessID();
    }

    @Override
    public MasterHeartBeat getHeartBeat() {
        SystemMetrics systemMetrics = metricsProvider.getSystemMetrics();
        return MasterHeartBeat.builder()
                .startupTime(ServerLifeCycleManager.getServerStartupTime())
                .reportTime(System.currentTimeMillis())
                .jvmCpuUsage(systemMetrics.getJvmCpuUsagePercentage())
                .cpuUsage(systemMetrics.getSystemCpuUsagePercentage())
                .jvmMemoryUsage(systemMetrics.getJvmMemoryUsedPercentage())
                .jvmHeapUsed(systemMetrics.getJvmHeapUsed())
                .jvmHeapMax(systemMetrics.getJvmHeapMax())
                .jvmNonHeapUsed(systemMetrics.getJvmNonHeapUsed())
                .jvmNonHeapMax(systemMetrics.getJvmNonHeapMax())
                .memoryUsage(systemMetrics.getSystemMemoryUsedPercentage())
                .diskUsage(systemMetrics.getDiskUsedPercentage())
                .processId(processId)
                .serverStatus(
                        masterServerLoadProtection.isOverload(systemMetrics) ? ServerStatus.BUSY : ServerStatus.NORMAL)
                .host(NetUtils.getHost())
                .port(masterConfig.getListenPort())
                .isCoordinator(masterCoordinator.isActive())
                .build();
    }

    @Override
    public void writeHeartBeat(final MasterHeartBeat masterHeartBeat) {
        final String failoverNodePath = RegistryUtils.getFailoveredNodePath(masterHeartBeat);
        if (registryClient.exists(failoverNodePath)) {
            log.warn("The master: {} is under {}, means it has been failover will close myself",
                    masterHeartBeat,
                    failoverNodePath);
            registryClient
                    .getStoppable(RegistryNodeType.MASTER)
                    .stop("The master exist: " + failoverNodePath + ", means it has been failover will close myself");
            return;
        }
        String masterHeartBeatJson = JSONUtils.toJsonString(masterHeartBeat);
        registryClient.persistEphemeral(heartBeatPath, masterHeartBeatJson);
        MasterServerMetrics.incMasterHeartbeatCount();
        log.debug("Success write master heartBeatInfo into registry, masterRegistryPath: {}, heartBeatInfo: {}",
                heartBeatPath,
                masterHeartBeatJson);
    }

}
