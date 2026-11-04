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

package io.yak.ops.api.service.impl;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.service.MonitorService;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.common.model.Server;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.plugin.api.monitor.DatabaseMetrics;
import io.yak.ops.dao.plugin.api.monitor.DatabaseMonitor;
import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.master.IWorkflowExecutorQueryClient;
import io.yak.ops.extract.master.dto.WorkflowExecutorDTO;
import io.yak.ops.extract.master.transportor.WorkflowExecutorQueryRequest;
import io.yak.ops.extract.master.transportor.WorkflowExecutorQueryResponse;
import io.yak.ops.extract.worker.ITaskExecutorQueryClient;
import io.yak.ops.extract.worker.transportor.TaskExecutorQueryRequest;
import io.yak.ops.extract.worker.transportor.TaskExecutorQueryResponse;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.enums.RegistryNodeType;
import io.yak.ops.task.executor.dto.TaskExecutorDTO;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MonitorServiceImpl extends BaseServiceImpl implements MonitorService {

    @Autowired
    private DatabaseMonitor databaseMonitor;

    @Autowired
    private RegistryClient registryClient;

    /**
     * query database state
     *
     * @param loginUser login user
     * @return data base state
     */
    @Override
    public List<DatabaseMetrics> queryDatabaseState(User loginUser) {
        return Lists.newArrayList(databaseMonitor.getDatabaseMetrics());
    }

    @Override
    public List<Server> listServer(RegistryNodeType nodeType) {
        return registryClient.getServerList(nodeType);
    }

    @Override
    public List<WorkflowExecutorDTO> queryWorkflowExecutors(User loginUser, String masterAddress) {
        if (!loginUser.getUserType().equals(UserType.ADMIN_USER)) {
            throw new ServiceException(Status.NO_CURRENT_OPERATING_PERMISSION);
        }

        WorkflowExecutorQueryResponse response = Clients
                .withService(IWorkflowExecutorQueryClient.class)
                .withHost(masterAddress)
                .queryWorkflowExecutors(new WorkflowExecutorQueryRequest());
        if (!response.isSuccess()) {
            throw new ServiceException(response.getMessage());
        }
        return response.getWorkflowExecutors();
    }

    @Override
    public List<TaskExecutorDTO> queryTaskExecutors(User loginUser, String serverAddress) {
        if (!loginUser.getUserType().equals(UserType.ADMIN_USER)) {
            throw new ServiceException(Status.NO_CURRENT_OPERATING_PERMISSION);
        }
        TaskExecutorQueryResponse response = Clients
                .withService(ITaskExecutorQueryClient.class)
                .withHost(serverAddress)
                .queryTaskInstances(new TaskExecutorQueryRequest());
        if (!response.isSuccess()) {
            throw new ServiceException(response.getMessage());
        }
        return response.getTaskExecutors();
    }

}
