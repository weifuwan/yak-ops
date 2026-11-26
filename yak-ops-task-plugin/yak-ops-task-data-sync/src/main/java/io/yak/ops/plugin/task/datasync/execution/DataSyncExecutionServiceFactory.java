package io.yak.ops.plugin.task.datasync.execution;

/** Creates the runtime-specific execution service for one CDC task execution. */
@FunctionalInterface
public interface DataSyncExecutionServiceFactory {

    DataSyncExecutionService create();
}
