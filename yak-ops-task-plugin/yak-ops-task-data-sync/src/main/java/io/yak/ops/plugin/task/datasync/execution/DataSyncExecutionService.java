package io.yak.ops.plugin.task.datasync.execution;

import org.apache.flink.cdc.composer.flink.FlinkPipelineExecution;

/** Owns the lifecycle of one Flink CDC execution. */
public interface DataSyncExecutionService {

    DataSyncExecutionHandle submit(
            FlinkPipelineExecution execution, String executionId, int taskInstanceId) throws Exception;

    DataSyncExecutionState queryState(DataSyncExecutionHandle handle) throws Exception;

    void cancel(DataSyncExecutionHandle handle) throws Exception;

    void release(DataSyncExecutionHandle handle) throws Exception;
}
