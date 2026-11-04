package io.yak.ops.api.executor.workflow;

import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.common.model.Server;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.master.IWorkflowControlClient;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRepeatRunningRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceRepeatRunningResponse;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.enums.RegistryNodeType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RepeatRunningWorkflowInstanceExecutorDelegate
        implements
            IExecutorDelegate<RepeatRunningWorkflowInstanceExecutorDelegate.RepeatRunningWorkflowInstanceOperation, Void> {

    @Autowired
    private RegistryClient registryClient;

    @Override
    public Void execute(RepeatRunningWorkflowInstanceOperation workflowInstanceControlRequest) {
        final WorkflowInstance workflowInstance = workflowInstanceControlRequest.workflowInstance;
        if (workflowInstance.getState() == null || !workflowInstance.getState().isFinalState()) {
            throw new ServiceException(
                    String.format("The workflow instance: %s status is %s, cannot repeat running",
                            workflowInstance.getName(), workflowInstance.getState()));
        }

        final Server masterServer = registryClient.getRandomServer(RegistryNodeType.MASTER).orElse(null);
        if (masterServer == null) {
            throw new ServiceException("no master server available");
        }
        final WorkflowInstanceRepeatRunningRequest repeatRunningRequest = WorkflowInstanceRepeatRunningRequest.builder()
                .workflowInstanceId(workflowInstance.getId())
                .userId(workflowInstanceControlRequest.executeUser.getId())
                .build();

        final WorkflowInstanceRepeatRunningResponse repeatRunningResponse = Clients
                .withService(IWorkflowControlClient.class)
                .withHost(masterServer.getHost() + ":" + masterServer.getPort())
                .repeatTriggerWorkflowInstance(repeatRunningRequest);
        if (!repeatRunningResponse.isSuccess()) {
            throw new ServiceException(
                    "Repeat running workflow instance failed: " + repeatRunningResponse.getMessage());
        }

        return null;
    }

    public static class RepeatRunningWorkflowInstanceOperation {

        private final RepeatRunningWorkflowInstanceExecutorDelegate repeatRunningWorkflowInstanceExecutorDelegate;

        private WorkflowInstance workflowInstance;

        private User executeUser;

        public RepeatRunningWorkflowInstanceOperation(RepeatRunningWorkflowInstanceExecutorDelegate repeatRunningWorkflowInstanceExecutorDelegate) {
            this.repeatRunningWorkflowInstanceExecutorDelegate = repeatRunningWorkflowInstanceExecutorDelegate;
        }

        public RepeatRunningWorkflowInstanceOperation onWorkflowInstance(WorkflowInstance workflowInstance) {
            this.workflowInstance = workflowInstance;
            return this;
        }

        public RepeatRunningWorkflowInstanceOperation byUser(User executeUser) {
            this.executeUser = executeUser;
            return this;
        }

        public void execute() {
            repeatRunningWorkflowInstanceExecutorDelegate.execute(this);
        }
    }

}
