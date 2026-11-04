package io.yak.ops.api.executor.workflow;

import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.SerialCommandDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.master.IWorkflowControlClient;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceStopRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstanceStopResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StopWorkflowInstanceExecutorDelegate
        implements
            IExecutorDelegate<StopWorkflowInstanceExecutorDelegate.StopWorkflowInstanceOperation, Void> {

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private SerialCommandDao serialCommandDao;

    @Override
    public Void execute(StopWorkflowInstanceOperation workflowInstanceControlRequest) {
        final WorkflowInstance workflowInstance = workflowInstanceControlRequest.workflowInstance;
        exceptionIfWorkflowInstanceCannotStop(workflowInstance);

        if (workflowInstance.getState().isCanDirectStopInDB()) {
            directStopInDB(workflowInstance);
        } else {
            stopInMaster(workflowInstance);
        }
        return null;
    }

    void exceptionIfWorkflowInstanceCannotStop(WorkflowInstance workflowInstance) {
        final WorkflowExecutionStatus workflowInstanceState = workflowInstance.getState();
        if (workflowInstanceState.isCanStop()) {
            return;
        }
        throw new ServiceException(
                "The workflow instance: " + workflowInstance.getName() + " status is " + workflowInstanceState
                        + ", can not stop");
    }

    void directStopInDB(WorkflowInstance workflowInstance) {
        // todo: move the stop logic to master
        transactionTemplate.execute(status -> {
            workflowInstanceDao.updateWorkflowInstanceState(
                    workflowInstance.getId(),
                    workflowInstance.getState(),
                    WorkflowExecutionStatus.STOP);
            serialCommandDao.deleteByWorkflowInstanceId(workflowInstance.getId());
            return null;
        });
        log.info("Update workflow instance {} state from: {} to {} success",
                workflowInstance.getName(),
                workflowInstance.getState().name(),
                WorkflowExecutionStatus.STOP.name());
    }

    void stopInMaster(WorkflowInstance workflowInstance) {
        try {
            final WorkflowInstanceStopResponse stopResponse = Clients
                    .withService(IWorkflowControlClient.class)
                    .withHost(workflowInstance.getHost())
                    .stopWorkflowInstance(new WorkflowInstanceStopRequest(workflowInstance.getId()));

            if (stopResponse != null && stopResponse.isSuccess()) {
                log.info("WorkflowInstance: {} stop success", workflowInstance.getName());
            } else {
                throw new ServiceException(
                        "WorkflowInstance: " + workflowInstance.getName() + " stop failed: " + stopResponse);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException(
                    String.format("WorkflowInstance: %s stop failed", workflowInstance.getName()), e);
        }
    }

    public static class StopWorkflowInstanceOperation {

        private final StopWorkflowInstanceExecutorDelegate stopWorkflowInstanceExecutorDelegate;

        private WorkflowInstance workflowInstance;

        private User executeUser;

        public StopWorkflowInstanceOperation(StopWorkflowInstanceExecutorDelegate stopWorkflowInstanceExecutorDelegate) {
            this.stopWorkflowInstanceExecutorDelegate = stopWorkflowInstanceExecutorDelegate;
        }

        public StopWorkflowInstanceExecutorDelegate.StopWorkflowInstanceOperation onWorkflowInstance(WorkflowInstance workflowInstance) {
            this.workflowInstance = workflowInstance;
            return this;
        }

        public StopWorkflowInstanceExecutorDelegate.StopWorkflowInstanceOperation byUser(User executeUser) {
            this.executeUser = executeUser;
            return this;
        }

        public void execute() {
            stopWorkflowInstanceExecutorDelegate.execute(this);
        }
    }
}
