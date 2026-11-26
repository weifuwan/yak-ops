package io.yak.ops.plugin.task.datasync.metrics;

import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionHandle;

/** Collects a normalized CDC metric snapshot for one execution. */
public interface DataSyncMetricsService {

    DataSyncExecutionSnapshot collect(DataSyncExecutionHandle handle) throws Exception;
}
