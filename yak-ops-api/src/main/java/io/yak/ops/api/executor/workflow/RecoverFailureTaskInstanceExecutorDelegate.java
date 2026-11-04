package io.yak.ops.api.executor.workflow;

import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.common.model.Server;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.master.IWorkflowControlClient;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverFailureTasksRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRecoverFailureTasksResponse;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.enums.RegistryNodeType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Component
public class RecoverFailureTaskInstanceExecutorDelegate
        implements
            IExecutorDelegate<RecoverFailureTaskInstanceExecutorDelegate.RecoverFailureTaskInstanceOperation, Void> {

    @Autowired
    private RegistryClient registryClient;

    @Override
    public Void execute(RecoverFailureTaskInstanceOperation recoverFailureTaskInstanceOperation) {
        WorkflowInstance workflowInstance = recoverFailureTaskInstanceOperation.getWorkflowInstance();
        if (!workflowInstance.getState().isFailure()) {
            throw new ServiceException(
                    String.format("The workflow instance: %s status is %s, can not be recovered",
                            workflowInstance.getName(), workflowInstance.getState()));
        }

        final Server masterServer = registryClient.getRandomServer(RegistryNodeType.MASTER).orElse(null);
        if (masterServer == null) {
            throw new ServiceException("no master server available");
        }
        final WorkflowInstanceRecoverFailureTasksRequest recoverFailureTaskRequest =
                WorkflowInstanceRecoverFailureTasksRequest.builder()
                        .workflowInstanceId(workflowInstance.getId())
                        .userId(recoverFailureTaskInstanceOperation.executeUser.getId())
                        .build();

        final WorkflowInstanceRecoverFailureTasksResponse recoverFailureTaskResponse = Clients
                .withService(IWorkflowControlClient.class)
                .withHost(masterServer.getHost() + ":" + masterServer.getPort())
                .triggerFromFailureTasks(recoverFailureTaskRequest);
        if (!recoverFailureTaskResponse.isSuccess()) {
            throw new ServiceException("Recover workflow instance failed: " + recoverFailureTaskResponse.getMessage());
        }
        return null;
    }

    @Getter
    public static class RecoverFailureTaskInstanceOperation {

        private final RecoverFailureTaskInstanceExecutorDelegate recoverFailureTaskInstanceExecutorDelegate;

        private WorkflowInstance workflowInstance;

        private User executeUser;

        public RecoverFailureTaskInstanceOperation(RecoverFailureTaskInstanceExecutorDelegate recoverFailureTaskInstanceExecutorDelegate) {
            this.recoverFailureTaskInstanceExecutorDelegate = recoverFailureTaskInstanceExecutorDelegate;
        }

        public RecoverFailureTaskInstanceOperation onWorkflowInstance(WorkflowInstance workflowInstance) {
            this.workflowInstance = workflowInstance;
            return this;
        }

        public RecoverFailureTaskInstanceOperation byUser(User executeUser) {
            this.executeUser = executeUser;
            return this;
        }

        public void execute() {
            recoverFailureTaskInstanceExecutorDelegate.execute(this);
        }
    }
}
