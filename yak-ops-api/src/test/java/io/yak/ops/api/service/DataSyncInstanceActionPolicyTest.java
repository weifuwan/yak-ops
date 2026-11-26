package io.yak.ops.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.api.enums.DataSyncInstanceAction;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class DataSyncInstanceActionPolicyTest {

    @Test
    void runningStreamInstanceOnlyOffersStop() {
        TaskInstance task = streamInstance(TaskExecutionStatus.RUNNING_EXECUTION);
        WorkflowInstance workflow = workflow(WorkflowExecutionStatus.RUNNING_EXECUTION);

        assertEquals(Collections.singletonList(DataSyncInstanceAction.STOP),
                DataSyncInstanceActionPolicy.availableActions(task, workflow, false));
    }

    @Test
    void stoppedStreamInstanceOffersResumeOnlyWhenRecoveryPointExists() {
        TaskInstance task = streamInstance(TaskExecutionStatus.KILL);
        WorkflowInstance workflow = workflow(WorkflowExecutionStatus.STOP);

        assertEquals(Collections.singletonList(DataSyncInstanceAction.RERUN),
                DataSyncInstanceActionPolicy.availableActions(task, workflow, false));
        assertEquals(Arrays.asList(DataSyncInstanceAction.RESUME, DataSyncInstanceAction.RERUN),
                DataSyncInstanceActionPolicy.availableActions(task, workflow, true));
    }

    @Test
    void failedStreamInstanceRequiresRecoveryPointForFailureRecovery() {
        TaskInstance task = streamInstance(TaskExecutionStatus.FAILURE);
        WorkflowInstance workflow = workflow(WorkflowExecutionStatus.FAILURE);

        assertEquals(Collections.singletonList(DataSyncInstanceAction.RERUN),
                DataSyncInstanceActionPolicy.availableActions(task, workflow, false));
        assertEquals(Arrays.asList(DataSyncInstanceAction.RECOVER_FAILURE, DataSyncInstanceAction.RERUN),
                DataSyncInstanceActionPolicy.availableActions(task, workflow, true));
    }

    @Test
    void faultToleranceStateIsNotActiveAndCannotBeManuallyOperatedDuringFailover() {
        TaskInstance task = streamInstance(TaskExecutionStatus.NEED_FAULT_TOLERANCE);
        WorkflowInstance workflow = workflow(WorkflowExecutionStatus.FAILOVER);

        assertFalse(DataSyncInstanceActionPolicy.isActive(TaskExecutionStatus.NEED_FAULT_TOLERANCE));
        assertTrue(DataSyncInstanceActionPolicy.availableActions(task, workflow, true).isEmpty());
    }

    @Test
    void faultToleranceStateCanRecoverAfterWorkflowFailureIsFinal() {
        TaskInstance task = streamInstance(TaskExecutionStatus.NEED_FAULT_TOLERANCE);
        WorkflowInstance workflow = workflow(WorkflowExecutionStatus.FAILURE);

        assertEquals(Arrays.asList(DataSyncInstanceAction.RECOVER_FAILURE, DataSyncInstanceAction.RERUN),
                DataSyncInstanceActionPolicy.availableActions(task, workflow, true));
    }

    @Test
    void batchActionsContinueToFollowWorkflowState() {
        TaskInstance task = new TaskInstance();
        task.setTaskExecuteType(TaskExecuteType.BATCH);
        task.setState(TaskExecutionStatus.FAILURE);

        assertEquals(Arrays.asList(DataSyncInstanceAction.RECOVER_FAILURE, DataSyncInstanceAction.RERUN),
                DataSyncInstanceActionPolicy.availableActions(
                        task, workflow(WorkflowExecutionStatus.FAILURE), false));
    }

    private static TaskInstance streamInstance(TaskExecutionStatus status) {
        TaskInstance task = new TaskInstance();
        task.setTaskExecuteType(TaskExecuteType.STREAM);
        task.setState(status);
        task.setHost("worker-1");
        return task;
    }

    private static WorkflowInstance workflow(WorkflowExecutionStatus status) {
        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setState(status);
        return workflow;
    }
}
