package io.yak.ops.plugin.task.datasync.execution;

import org.apache.flink.cdc.composer.flink.FlinkPipelineExecution;

/** Owns the lifecycle of one Flink CDC execution. */
public interface DataSyncExecutionService {

    default DataSyncExecutionHandle submit(
            FlinkPipelineExecution execution, String executionId, int taskInstanceId) throws Exception {
        return submit(execution, executionId, taskInstanceId, null);
    }

    DataSyncExecutionHandle submit(
            FlinkPipelineExecution execution, String executionId,
            int taskInstanceId, String restorePath) throws Exception;

    DataSyncExecutionState queryState(DataSyncExecutionHandle handle) throws Exception;

    /** Returns the latest completed, externally-restorable Flink checkpoint, if one exists. */
    DataSyncRecoveryPointSnapshot getLatestCompletedCheckpoint(DataSyncExecutionHandle handle) throws Exception;

    void cancel(DataSyncExecutionHandle handle) throws Exception;

    String triggerSavepoint(DataSyncExecutionHandle handle) throws Exception;

    String stopWithSavepoint(DataSyncExecutionHandle handle) throws Exception;

    void release(DataSyncExecutionHandle handle) throws Exception;
}
