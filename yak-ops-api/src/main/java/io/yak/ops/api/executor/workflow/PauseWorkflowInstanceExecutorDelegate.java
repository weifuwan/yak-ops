package io.yak.ops.api.executor.workflow;

import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.SerialCommandDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.master.IWorkflowControlClient;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstancePauseRequest;
import io.yak.ops.extract.master.transportor.workflow.WorkflowInstancePauseResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PauseWorkflowInstanceExecutorDelegate
        implements
            IExecutorDelegate<PauseWorkflowInstanceExecutorDelegate.PauseWorkflowInstanceOperation, Void> {

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private SerialCommandDao serialCommandDao;

    @Override
    public Void execute(PauseWorkflowInstanceOperation workflowInstanceControlRequest) {
        final WorkflowInstance workflowInstance = workflowInstanceControlRequest.workflowInstance;
        exceptionIfWorkflowInstanceCannotPause(workflowInstance);
        if (workflowInstance.getState().isCanDirectPauseInDB()) {
            directPauseInDB(workflowInstance);
        } else {
            pauseInMaster(workflowInstance);
        }
        return null;
    }

    private void exceptionIfWorkflowInstanceCannotPause(WorkflowInstance workflowInstance) {
        WorkflowExecutionStatus workflowInstanceState = workflowInstance.getState();
        if (workflowInstanceState.isCanPause()) {
            return;
        }
        throw new ServiceException(
                "The workflow instance: " + workflowInstance.getName() + " status is " + workflowInstanceState
                        + ", can not pause");
    }

    private void directPauseInDB(WorkflowInstance workflowInstance) {
        // todo: move the pause logic to master
        transactionTemplate.execute(status -> {
            workflowInstanceDao.updateWorkflowInstanceState(
                    workflowInstance.getId(),
                    workflowInstance.getState(),
                    WorkflowExecutionStatus.PAUSE);
            serialCommandDao.deleteByWorkflowInstanceId(workflowInstance.getId());
            return null;
        });
        log.info("Update workflow instance {} state from: {} to {} success",
                workflowInstance.getName(),
                workflowInstance.getState().name(),
                WorkflowExecutionStatus.PAUSE.name());
    }

    private void pauseInMaster(WorkflowInstance workflowInstance) {
        try {
            final WorkflowInstancePauseResponse pauseResponse = Clients
                    .withService(IWorkflowControlClient.class)
                    .withHost(workflowInstance.getHost())
                    .pauseWorkflowInstance(new WorkflowInstancePauseRequest(workflowInstance.getId()));

            if (pauseResponse != null && pauseResponse.isSuccess()) {
                log.info("WorkflowInstance: {} pause success", workflowInstance.getName());
            } else {
                throw new ServiceException(
                        "WorkflowInstance: " + workflowInstance.getName() + " pause failed: " + pauseResponse);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException(
                    String.format("WorkflowInstance: %s pause failed", workflowInstance.getName()), e);
        }
    }

    public static class PauseWorkflowInstanceOperation {

        private final PauseWorkflowInstanceExecutorDelegate pauseWorkflowInstanceExecutorDelegate;

        private WorkflowInstance workflowInstance;

        private User executeUser;

        public PauseWorkflowInstanceOperation(PauseWorkflowInstanceExecutorDelegate pauseWorkflowInstanceExecutorDelegate) {
            this.pauseWorkflowInstanceExecutorDelegate = pauseWorkflowInstanceExecutorDelegate;
        }

        public PauseWorkflowInstanceExecutorDelegate.PauseWorkflowInstanceOperation onWorkflowInstance(WorkflowInstance workflowInstance) {
            this.workflowInstance = workflowInstance;
            return this;
        }

        public PauseWorkflowInstanceExecutorDelegate.PauseWorkflowInstanceOperation byUser(User executeUser) {
            this.executeUser = executeUser;
            return this;
        }

        public void execute() {
            pauseWorkflowInstanceExecutorDelegate.execute(this);
        }
    }
}
