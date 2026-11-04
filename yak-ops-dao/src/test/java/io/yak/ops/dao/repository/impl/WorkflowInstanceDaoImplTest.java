package io.yak.ops.dao.repository.impl;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.WorkflowInstanceDao;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkflowInstanceDaoImplTest extends BaseDaoTest {

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Test
    void queryByWorkflowCodeVersionStatus_EMPTY_INSTANCE() {
        long workflowDefinitionCode = 1L;
        int workflowDefinitionVersion = 1;

        assertTrue(isEmpty(workflowInstanceDao.queryByWorkflowCodeVersionStatus(workflowDefinitionCode,
                workflowDefinitionVersion, WorkflowExecutionStatus.NEED_FAILOVER_STATES)));
    }

    @Test
    void queryByWorkflowCodeVersionStatus_EXIST_NOT_FINISH_INSTANCE() {
        long workflowDefinitionCode = 1L;
        int workflowDefinitionVersion = 1;
        int[] status = WorkflowExecutionStatus.NOT_TERMINAL_STATES;

        assertTrue(isEmpty(workflowInstanceDao.queryByWorkflowCodeVersionStatus(workflowDefinitionCode,
                workflowDefinitionVersion, status)));

        workflowInstanceDao.insert(createWorkflowInstance(workflowDefinitionCode, workflowDefinitionVersion,
                WorkflowExecutionStatus.RUNNING_EXECUTION));
        workflowInstanceDao.insert(createWorkflowInstance(workflowDefinitionCode, workflowDefinitionVersion,
                WorkflowExecutionStatus.READY_PAUSE));
        workflowInstanceDao.insert(createWorkflowInstance(workflowDefinitionCode, workflowDefinitionVersion,
                WorkflowExecutionStatus.READY_STOP));
        workflowInstanceDao.insert(createWorkflowInstance(workflowDefinitionCode, workflowDefinitionVersion,
                WorkflowExecutionStatus.SERIAL_WAIT));
        assertEquals(4, workflowInstanceDao
                .queryByWorkflowCodeVersionStatus(workflowDefinitionCode, workflowDefinitionVersion, status).size());
    }

    @Test
    void updateWorkflowInstanceState_success() {
        WorkflowInstance workflowInstance = createWorkflowInstance(
                1L, 1, WorkflowExecutionStatus.RUNNING_EXECUTION);
        workflowInstanceDao.insert(workflowInstance);

        assertDoesNotThrow(() -> workflowInstanceDao.updateWorkflowInstanceState(
                workflowInstance.getId(),
                WorkflowExecutionStatus.RUNNING_EXECUTION,
                WorkflowExecutionStatus.SUCCESS));
    }

    @Test
    void updateWorkflowInstanceState_failed() {
        WorkflowInstance workflowInstance = createWorkflowInstance(
                1L, 1, WorkflowExecutionStatus.RUNNING_EXECUTION);
        workflowInstanceDao.insert(workflowInstance);

        UnsupportedOperationException unsupportedOperationException = assertThrows(UnsupportedOperationException.class,
                () -> workflowInstanceDao.updateWorkflowInstanceState(
                        workflowInstance.getId(),
                        WorkflowExecutionStatus.READY_STOP,
                        WorkflowExecutionStatus.STOP));
        Assertions.assertEquals("updateWorkflowInstance " + workflowInstance.getId()
                + " state failed, expect original state is " + WorkflowExecutionStatus.READY_STOP.name()
                + " actual state is : {} " + workflowInstance.getState().name(),
                unsupportedOperationException.getMessage());
    }

    @Test
    void forceUpdateWorkflowInstanceState() {
        WorkflowInstance workflowInstance = createWorkflowInstance(1L, 1, WorkflowExecutionStatus.RUNNING_EXECUTION);
        workflowInstanceDao.insert(workflowInstance);
        workflowInstanceDao.forceUpdateWorkflowInstanceState(workflowInstance.getId(), WorkflowExecutionStatus.FAILURE);
        assertEquals(WorkflowExecutionStatus.FAILURE,
                workflowInstanceDao.queryById(workflowInstance.getId()).getState());
    }

    @Test
    void queryByWorkflowCodeVersionStatus_EXIST_FINISH_INSTANCE() {
        long workflowDefinitionCode = 1L;
        int workflowDefinitionVersion = 1;
        int[] status = WorkflowExecutionStatus.NOT_TERMINAL_STATES;

        workflowInstanceDao.insert(createWorkflowInstance(workflowDefinitionCode, workflowDefinitionVersion,
                WorkflowExecutionStatus.PAUSE));
        workflowInstanceDao.insert(createWorkflowInstance(workflowDefinitionCode, workflowDefinitionVersion,
                WorkflowExecutionStatus.STOP));
        workflowInstanceDao.insert(createWorkflowInstance(workflowDefinitionCode, workflowDefinitionVersion,
                WorkflowExecutionStatus.FAILURE));
        workflowInstanceDao.insert(createWorkflowInstance(workflowDefinitionCode, workflowDefinitionVersion,
                WorkflowExecutionStatus.SUCCESS));
        assertTrue(isEmpty(workflowInstanceDao.queryByWorkflowCodeVersionStatus(workflowDefinitionCode,
                workflowDefinitionVersion, status)));
    }

    private WorkflowInstance createWorkflowInstance(Long workflowDefinitionCode, int workflowDefinitionVersion,
                                                    WorkflowExecutionStatus status) {
        WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.setName("WorkflowInstance" + System.currentTimeMillis());
        workflowInstance.setWorkflowDefinitionCode(workflowDefinitionCode);
        workflowInstance.setWorkflowDefinitionVersion(workflowDefinitionVersion);
        workflowInstance.setState(status);
        return workflowInstance;
    }

}
