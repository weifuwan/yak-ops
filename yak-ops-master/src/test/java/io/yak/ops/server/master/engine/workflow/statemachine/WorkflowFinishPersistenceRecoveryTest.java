package io.yak.ops.server.master.engine.workflow.statemachine;

import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.common.enums.WorkflowExecutionTypeEnum;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.SerialCommandDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.server.master.engine.WorkflowEventBus;
import io.yak.ops.server.master.engine.workflow.execution.IWorkflowExecution;
import io.yak.ops.server.master.engine.workflow.lifecycle.event.WorkflowFinalizeLifecycleEvent;
import io.yak.ops.server.master.runner.IWorkflowExecuteContext;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionCallback;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class WorkflowFinishPersistenceRecoveryTest {

    @Test
    void shouldKeepSharedWorkflowStateUnchangedUntilTransactionSucceeds() {
        WorkflowInstanceDao workflowInstanceDao = Mockito.mock(WorkflowInstanceDao.class);
        SerialCommandDao serialCommandDao = Mockito.mock(SerialCommandDao.class);
        TransactionTemplate transactionTemplate = Mockito.mock(TransactionTemplate.class);

        WorkflowRunningStateAction action = new WorkflowRunningStateAction();
        action.workflowInstanceDao = workflowInstanceDao;
        action.serialCommandDao = serialCommandDao;
        action.transactionTemplate = transactionTemplate;

        WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.setId(1);
        workflowInstance.setName("retryable-workflow");
        workflowInstance.setWorkflowDefinitionCode(100L);
        workflowInstance.setState(WorkflowExecutionStatus.RUNNING_EXECUTION);

        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setExecutionType(WorkflowExecutionTypeEnum.PARALLEL);

        IWorkflowExecuteContext executeContext = Mockito.mock(IWorkflowExecuteContext.class);
        Mockito.when(executeContext.getWorkflowDefinition()).thenReturn(workflowDefinition);

        WorkflowEventBus eventBus = Mockito.mock(WorkflowEventBus.class);
        IWorkflowExecution workflowExecution = Mockito.mock(IWorkflowExecution.class);
        Mockito.when(workflowExecution.getWorkflowInstance()).thenReturn(workflowInstance);
        Mockito.when(workflowExecution.getWorkflowExecuteContext()).thenReturn(executeContext);
        Mockito.when(workflowExecution.getWorkflowEventBus()).thenReturn(eventBus);
        Mockito.when(workflowExecution.getName()).thenReturn(workflowInstance.getName());

        Mockito.when(transactionTemplate.execute(Mockito.<TransactionCallback<Object>>any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<Object> callback = invocation.getArgument(0);
                    return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
                });
        Mockito.when(workflowInstanceDao.updateById(Mockito.any(WorkflowInstance.class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"))
                .thenReturn(true);

        Assertions.assertThrows(
                DataAccessResourceFailureException.class,
                () -> action.workflowFinish(workflowExecution, WorkflowExecutionStatus.SUCCESS));

        Assertions.assertEquals(WorkflowExecutionStatus.RUNNING_EXECUTION, workflowInstance.getState());
        Assertions.assertNull(workflowInstance.getEndTime());
        Mockito.verify(eventBus, Mockito.never()).publish(Mockito.any(WorkflowFinalizeLifecycleEvent.class));

        action.workflowFinish(workflowExecution, WorkflowExecutionStatus.SUCCESS);

        Assertions.assertEquals(WorkflowExecutionStatus.SUCCESS, workflowInstance.getState());
        Assertions.assertNotNull(workflowInstance.getEndTime());
        Mockito.verify(eventBus).publish(Mockito.any(WorkflowFinalizeLifecycleEvent.class));
        Mockito.verify(serialCommandDao, Mockito.never()).deleteByWorkflowInstanceId(Mockito.anyInt());
    }
}
