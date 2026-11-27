package io.yak.ops.plugin.task.datasync.execution;

import org.apache.flink.cdc.composer.flink.FlinkPipelineExecution;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared execution lifecycle rules.
 *
 * <p>Concrete implementations own the runtime-specific resources; this class only enforces
 * validation and idempotent release.
 */
public abstract class AbstractDataSyncExecutionService implements DataSyncExecutionService {

    private final Set<String> releasedExecutions = ConcurrentHashMap.newKeySet();

    @Override
    public final DataSyncExecutionHandle submit(
                                                FlinkPipelineExecution execution, String executionId,
                                                int taskInstanceId, String restorePath) throws Exception {
        if (execution == null || executionId == null || executionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Execution and executionId are required");
        }
        if (taskInstanceId <= 0) {
            throw new IllegalArgumentException("taskInstanceId must be positive");
        }
        releasedExecutions.remove(executionId);
        return doSubmit(execution, executionId, taskInstanceId, restorePath);
    }

    @Override
    public final DataSyncExecutionState queryState(DataSyncExecutionHandle handle) throws Exception {
        requireActive(handle);
        return doQueryState(handle);
    }

    @Override
    public final DataSyncRecoveryPointSnapshot getLatestCompletedCheckpoint(DataSyncExecutionHandle handle)
            throws Exception {
        requireActive(handle);
        return doGetLatestCompletedCheckpoint(handle);
    }

    @Override
    public final void cancel(DataSyncExecutionHandle handle) throws Exception {
        if (handle == null || releasedExecutions.contains(handle.getExecutionId())) {
            return;
        }
        doCancel(handle);
    }

    @Override
    public final String triggerSavepoint(DataSyncExecutionHandle handle) throws Exception {
        requireActive(handle);
        return doTriggerSavepoint(handle);
    }

    @Override
    public final String stopWithSavepoint(DataSyncExecutionHandle handle) throws Exception {
        requireActive(handle);
        return doStopWithSavepoint(handle);
    }

    @Override
    public final void release(DataSyncExecutionHandle handle) throws Exception {
        if (handle == null || !releasedExecutions.add(handle.getExecutionId())) {
            return;
        }
        try {
            doRelease(handle);
        } catch (Exception exception) {
            releasedExecutions.remove(handle.getExecutionId());
            throw exception;
        }
    }

    protected abstract DataSyncExecutionHandle doSubmit(
                                                        FlinkPipelineExecution execution, String executionId,
                                                        int taskInstanceId, String restorePath) throws Exception;

    protected abstract String doTriggerSavepoint(DataSyncExecutionHandle handle) throws Exception;

    protected abstract String doStopWithSavepoint(DataSyncExecutionHandle handle) throws Exception;

    protected abstract DataSyncExecutionState doQueryState(DataSyncExecutionHandle handle) throws Exception;

    protected abstract DataSyncRecoveryPointSnapshot doGetLatestCompletedCheckpoint(
                                                                                    DataSyncExecutionHandle handle) throws Exception;

    protected abstract void doCancel(DataSyncExecutionHandle handle) throws Exception;

    protected abstract void doRelease(DataSyncExecutionHandle handle) throws Exception;

    private void requireActive(DataSyncExecutionHandle handle) {
        if (handle == null || releasedExecutions.contains(handle.getExecutionId())) {
            throw new IllegalStateException("CDC execution is not active");
        }
    }
}
