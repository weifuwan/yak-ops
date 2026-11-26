package io.yak.ops.plugin.task.datasync.execution;

/** Default factory for the current embedded MiniCluster deployment. */
public class MiniClusterDataSyncExecutionServiceFactory implements DataSyncExecutionServiceFactory {

    @Override
    public DataSyncExecutionService create() {
        return new MiniClusterDataSyncExecutionService();
    }
}
