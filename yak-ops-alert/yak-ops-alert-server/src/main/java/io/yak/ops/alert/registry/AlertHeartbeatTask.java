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

package io.yak.ops.alert.registry;

import io.yak.ops.alert.config.AlertConfig;
import io.yak.ops.alert.metrics.AlertServerMetrics;
import io.yak.ops.alert.service.AlertHAServer;
import io.yak.ops.common.enums.ServerStatus;
import io.yak.ops.common.model.AlertServerHeartBeat;
import io.yak.ops.common.model.BaseHeartBeatTask;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.common.utils.NetUtils;
import io.yak.ops.common.utils.OSUtils;
import io.yak.ops.meter.metrics.MetricsProvider;
import io.yak.ops.meter.metrics.SystemMetrics;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.enums.RegistryNodeType;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AlertHeartbeatTask extends BaseHeartBeatTask<AlertServerHeartBeat> {

    private final AlertConfig alertConfig;
    private final Integer processId;
    private final RegistryClient registryClient;

    private final MetricsProvider metricsProvider;

    private final AlertHAServer alertHAServer;
    private final String heartBeatPath;
    private final long startupTime;

    public AlertHeartbeatTask(AlertConfig alertConfig,
                              MetricsProvider metricsProvider,
                              RegistryClient registryClient,
                              AlertHAServer alertHAServer) {
        super("AlertHeartbeatTask", alertConfig.getMaxHeartbeatInterval().toMillis());
        this.startupTime = System.currentTimeMillis();
        this.alertConfig = alertConfig;
        this.metricsProvider = metricsProvider;
        this.registryClient = registryClient;
        this.heartBeatPath =
                RegistryNodeType.ALERT_SERVER.getRegistryPath() + "/" + alertConfig.getAlertServerAddress();
        this.alertHAServer = alertHAServer;
        this.processId = OSUtils.getProcessID();
    }

    @Override
    public AlertServerHeartBeat getHeartBeat() {
        SystemMetrics systemMetrics = metricsProvider.getSystemMetrics();
        return AlertServerHeartBeat.builder()
                .processId(processId)
                .startupTime(startupTime)
                .reportTime(System.currentTimeMillis())
                .jvmCpuUsage(systemMetrics.getJvmCpuUsagePercentage())
                .cpuUsage(systemMetrics.getSystemCpuUsagePercentage())
                .memoryUsage(systemMetrics.getSystemMemoryUsedPercentage())
                .jvmMemoryUsage(systemMetrics.getJvmMemoryUsedPercentage())
                .diskUsage(systemMetrics.getDiskUsedPercentage())
                .serverStatus(ServerStatus.NORMAL)
                .isActive(alertHAServer.isActive())
                .host(NetUtils.getHost())
                .port(alertConfig.getPort())
                .build();
    }

    @Override
    public void writeHeartBeat(AlertServerHeartBeat heartBeat) {
        String heartBeatJson = JSONUtils.toJsonString(heartBeat);
        registryClient.persistEphemeral(heartBeatPath, heartBeatJson);
        AlertServerMetrics.incAlertHeartbeatCount();
        log.debug("Success write alert heartBeatInfo into registry, alertRegistryPath: {}, heartBeatInfo: {}",
                heartBeatPath, heartBeatJson);
    }
}
