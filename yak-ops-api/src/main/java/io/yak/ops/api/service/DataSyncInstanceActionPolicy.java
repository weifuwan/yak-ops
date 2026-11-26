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
        if (instance == null || instance.getState() == null) {
            return Collections.emptyList();
        }
        if (workflow != null && workflow.getState() == WorkflowExecutionStatus.FAILOVER) {
            return Collections.emptyList();
        }

        List<DataSyncInstanceAction> actions = new ArrayList<>();
        if (instance.getTaskExecuteType() == TaskExecuteType.STREAM) {
            appendStreamActions(instance, actions);
            return actions;
        }

        if (workflow == null || workflow.getState() == null) {
            return actions;
        }

        WorkflowExecutionStatus workflowState = workflow.getState();
        if (workflowState.canStop()) {
            actions.add(DataSyncInstanceAction.STOP);
        } else if (workflowState == WorkflowExecutionStatus.PAUSE
                || workflowState == WorkflowExecutionStatus.STOP) {
            actions.add(DataSyncInstanceAction.RESUME);
            actions.add(DataSyncInstanceAction.RERUN);
        } else if (workflowState == WorkflowExecutionStatus.FAILURE) {
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
            TaskInstance instance, List<DataSyncInstanceAction> actions) {
        TaskExecutionStatus status = instance.getState();
        if (isActive(status)) {
            if (StringUtils.isNotBlank(instance.getHost())) {
                actions.add(DataSyncInstanceAction.STOP);
            }
            return;
        }

        if (status == TaskExecutionStatus.KILL || status == TaskExecutionStatus.PAUSE) {
            actions.add(DataSyncInstanceAction.RESUME);
            actions.add(DataSyncInstanceAction.RERUN);
        } else if (status == TaskExecutionStatus.FAILURE
                || status == TaskExecutionStatus.NEED_FAULT_TOLERANCE) {
            actions.add(DataSyncInstanceAction.RECOVER_FAILURE);
            actions.add(DataSyncInstanceAction.RERUN);
        } else if (status == TaskExecutionStatus.SUCCESS
                || status == TaskExecutionStatus.FORCED_SUCCESS) {
            actions.add(DataSyncInstanceAction.RERUN);
        }
    }
}
