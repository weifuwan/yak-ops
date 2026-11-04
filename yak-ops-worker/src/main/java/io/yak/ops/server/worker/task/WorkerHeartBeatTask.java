/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.server.worker.task;

import io.yak.ops.common.enums.ServerStatus;
import io.yak.ops.common.lifecycle.ServerLifeCycleManager;
import io.yak.ops.common.model.BaseHeartBeatTask;
import io.yak.ops.common.model.WorkerHeartBeat;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.common.utils.NetUtils;
import io.yak.ops.common.utils.OSUtils;
import io.yak.ops.meter.metrics.MetricsProvider;
import io.yak.ops.meter.metrics.SystemMetrics;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.utils.RegistryUtils;
import io.yak.ops.server.worker.config.WorkerConfig;
import io.yak.ops.server.worker.config.WorkerServerLoadProtection;
import io.yak.ops.server.worker.metrics.WorkerServerMetrics;
import io.yak.ops.task.executor.container.ITaskExecutorContainer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkerHeartBeatTask extends BaseHeartBeatTask<WorkerHeartBeat> {

    private final WorkerConfig workerConfig;
    private final WorkerServerLoadProtection workerServerLoadProtection;
    private final RegistryClient registryClient;

    private final MetricsProvider metricsProvider;

    private final int processId;

    private final ITaskExecutorContainer taskExecutorContainer;

    public WorkerHeartBeatTask(@NonNull WorkerConfig workerConfig,
                               @NonNull WorkerServerLoadProtection workerServerLoadProtection,
                               @NonNull MetricsProvider metricsProvider,
                               @NonNull RegistryClient registryClient,
                               @NonNull ITaskExecutorContainer taskExecutorContainer) {
        super("WorkerHeartBeatTask", workerConfig.getMaxHeartbeatInterval().toMillis());
        this.workerServerLoadProtection = workerServerLoadProtection;
        this.metricsProvider = metricsProvider;
        this.workerConfig = workerConfig;
        this.registryClient = registryClient;
        this.taskExecutorContainer = taskExecutorContainer;
        this.processId = OSUtils.getProcessID();
    }

    @Override
    public WorkerHeartBeat getHeartBeat() {
        SystemMetrics systemMetrics = metricsProvider.getSystemMetrics();

        return WorkerHeartBeat.builder()
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
                .workerHostWeight(workerConfig.getHostWeight())
                .threadPoolUsage(taskExecutorContainer.slotUsage())
                .serverStatus(
                        workerServerLoadProtection.isOverload(systemMetrics) ? ServerStatus.BUSY : ServerStatus.NORMAL)
                .host(NetUtils.getHost())
                .port(workerConfig.getListenPort())
                .workerGroup(workerConfig.getGroup())
                .build();
    }

    @Override
    public void writeHeartBeat(final WorkerHeartBeat workerHeartBeat) {
        final String failoverNodePath = RegistryUtils.getFailoveredNodePath(workerHeartBeat);
        if (registryClient.exists(failoverNodePath)) {
            log.warn("The worker: {} is under {}, means it has been failover will close myself",
                    workerHeartBeat,
                    failoverNodePath);
            registryClient
                    .getStoppable()
                    .stop("The worker exist: " + failoverNodePath + ", means it has been failover will close myself");
            return;
        }
        String workerHeartBeatJson = JSONUtils.toJsonString(workerHeartBeat);
        String workerRegistryPath = workerConfig.getWorkerRegistryPath();
        registryClient.persistEphemeral(workerRegistryPath, workerHeartBeatJson);
        WorkerServerMetrics.incWorkerHeartbeatCount();
        log.debug(
                "Success write worker group heartBeatInfo into registry, workerRegistryPath: {} workerHeartBeatInfo: {}",
                workerRegistryPath,
                workerHeartBeatJson);
    }

}
