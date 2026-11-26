package io.yak.ops.api.service;

import io.yak.ops.api.enums.DataSyncInstanceAction;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.common.enums.WorkflowExecutionStatus;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/** Centralized operation policy shared by instance queries and action validation. */
public final class DataSyncInstanceActionPolicy {

    private DataSyncInstanceActionPolicy() {
    }

    public static List<DataSyncInstanceAction> availableActions(
            TaskInstance instance, WorkflowInstance workflow) {
        if (instance == null || instance.getState() == null
                || workflow == null || workflow.getState() == null
                || workflow.getState() == WorkflowExecutionStatus.FAILOVER) {
            return Collections.emptyList();
        }

        List<DataSyncInstanceAction> actions = new ArrayList<>();
        WorkflowExecutionStatus workflowState = workflow.getState();
        if (instance.getTaskExecuteType() == TaskExecuteType.STREAM) {
            appendStreamActions(instance, workflowState, actions);
            return actions;
        }

        if (workflowState.isCanStop()) {
            actions.add(DataSyncInstanceAction.STOP);
        } else if (workflowState.isPaused() || workflowState.isStopped()) {
            actions.add(DataSyncInstanceAction.RESUME);
            actions.add(DataSyncInstanceAction.RERUN);
        } else if (workflowState.isFailure()) {
            actions.add(DataSyncInstanceAction.RECOVER_FAILURE);
            actions.add(DataSyncInstanceAction.RERUN);
        } else if (workflowState.isFinalState()) {
            actions.add(DataSyncInstanceAction.RERUN);
        }
        return actions;
    }

    public static boolean isActive(TaskExecutionStatus status) {
        return status == TaskExecutionStatus.SUBMITTED_SUCCESS
                || status == TaskExecutionStatus.DISPATCH
                || status == TaskExecutionStatus.RUNNING_EXECUTION
                || status == TaskExecutionStatus.DELAY_EXECUTION;
    }

    private static void appendStreamActions(
            TaskInstance instance,
            WorkflowExecutionStatus workflowState,
            List<DataSyncInstanceAction> actions) {
        TaskExecutionStatus status = instance.getState();
        if (isActive(status)) {
            if (workflowState.isCanStop() && StringUtils.isNotBlank(instance.getHost())) {
                actions.add(DataSyncInstanceAction.STOP);
            }
            return;
        }

        if (status.isKill() && workflowState.isStopped()) {
            actions.add(DataSyncInstanceAction.RESUME);
            actions.add(DataSyncInstanceAction.RERUN);
        } else if (status.isPause() && workflowState.isPaused()) {
            actions.add(DataSyncInstanceAction.RESUME);
            actions.add(DataSyncInstanceAction.RERUN);
        } else if (status.isFailure() && workflowState.isFailure()) {
            actions.add(DataSyncInstanceAction.RECOVER_FAILURE);
            actions.add(DataSyncInstanceAction.RERUN);
        } else if ((status.isSuccess() || status.isForceSuccess()) && workflowState.isSuccess()) {
            actions.add(DataSyncInstanceAction.RERUN);
        }
    }
}
