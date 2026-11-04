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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>DolphinScheduler master register client, used to connect to registry and hand the registry events.
 * <p>When the Master node startup, it will register in registry center. And start a {@link MasterHeartBeatTask} to update its metadata in registry.
 */
@Component
@Slf4j
public class MasterRegistryClient implements AutoCloseable {

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
        registryClient.setStoppable(stoppable);
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

        while (!registryClient.checkNodeExists(NetUtils.getHost(), RegistryNodeType.MASTER)) {
            log.warn("The current master server node:{} cannot find in registry", NetUtils.getHost());
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
            registryClient.close();
        } catch (Exception e) {
            log.error("MasterServer remove registry path exception ", e);
        }
    }

    public boolean isAvailable() {
        return registryClient.isConnected();
    }
}
