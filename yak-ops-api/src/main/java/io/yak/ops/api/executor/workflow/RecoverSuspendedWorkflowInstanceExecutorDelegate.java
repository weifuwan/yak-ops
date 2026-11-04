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

package io.yak.ops.api.executor.workflow;

import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.common.model.Server;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.master.IWorkflowControlClient;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverSuspendTasksRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverSuspendTasksResponse;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.enums.RegistryNodeType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RecoverSuspendedWorkflowInstanceExecutorDelegate
        implements
            IExecutorDelegate<RecoverSuspendedWorkflowInstanceExecutorDelegate.RecoverSuspendedWorkflowInstanceOperation, Void> {

    @Autowired
    private RegistryClient registryClient;

    @Override
    public Void execute(RecoverSuspendedWorkflowInstanceOperation workflowInstanceControlRequest) {
        final WorkflowInstance workflowInstance = workflowInstanceControlRequest.workflowInstance;
        if (!workflowInstance.getState().isPaused() && !workflowInstance.getState().isStopped()) {
            throw new ServiceException(
                    String.format("The workflow instance: %s state is %s, cannot recovery", workflowInstance.getName(),
                            workflowInstance.getState()));
        }
        final Server masterServer = registryClient.getRandomServer(RegistryNodeType.MASTER).orElse(null);
        if (masterServer == null) {
            throw new ServiceException("no master server available");
        }
        final WorkflowInstanceRecoverSuspendTasksRequest recoverSuspendTaskRequest =
                WorkflowInstanceRecoverSuspendTasksRequest.builder()
                        .workflowInstanceId(workflowInstance.getId())
                        .userId(workflowInstanceControlRequest.executeUser.getId())
                        .build();

        final WorkflowInstanceRecoverSuspendTasksResponse recoverSuspendTaskResponse = Clients
                .withService(IWorkflowControlClient.class)
                .withHost(masterServer.getHost() + ":" + masterServer.getPort())
                .triggerFromSuspendTasks(recoverSuspendTaskRequest);
        if (!recoverSuspendTaskResponse.isSuccess()) {
            throw new ServiceException("Recover workflow instance failed: " + recoverSuspendTaskResponse.getMessage());
        }
        return null;
    }

    public static class RecoverSuspendedWorkflowInstanceOperation {

        private final RecoverSuspendedWorkflowInstanceExecutorDelegate recoverSuspendedWorkflowInstanceExecutorDelegate;

        private WorkflowInstance workflowInstance;

        private User executeUser;

        public RecoverSuspendedWorkflowInstanceOperation(RecoverSuspendedWorkflowInstanceExecutorDelegate recoverSuspendedWorkflowInstanceExecutorDelegate) {
            this.recoverSuspendedWorkflowInstanceExecutorDelegate = recoverSuspendedWorkflowInstanceExecutorDelegate;
        }

        public RecoverSuspendedWorkflowInstanceExecutorDelegate.RecoverSuspendedWorkflowInstanceOperation onWorkflowInstance(WorkflowInstance workflowInstance) {
            this.workflowInstance = workflowInstance;
            return this;
        }

        public RecoverSuspendedWorkflowInstanceExecutorDelegate.RecoverSuspendedWorkflowInstanceOperation byUser(User executeUser) {
            this.executeUser = executeUser;
            return this;
        }

        public void execute() {
            recoverSuspendedWorkflowInstanceExecutorDelegate.execute(this);
        }
    }
}
