package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import java.time.LocalDateTime;

/**
 * 单表执行或持久化重试恢复的冻结运行参数。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
record SingleTableRunContext(
        String workspaceId,
        String executionId,
        DataSyncDefinitionSnapshotVO snapshot,
        int firstAttemptNo,
        int maxAttempts,
        int backoffSeconds,
        DataSyncInstanceStatus expectedExecutionStatus,
        LocalDateTime initialRetryTime) {

    static SingleTableRunContext submission(
            String workspaceId, String executionId, DataSyncDefinitionSnapshotVO snapshot) {
        ExecutionRetryPolicy policy = ExecutionRetryPolicy.from(snapshot.getRetryPolicy());
        return new SingleTableRunContext(
                workspaceId,
                executionId,
                snapshot,
                1,
                policy.maxAttempts(),
                policy.baseBackoffSeconds(),
                DataSyncInstanceStatus.PENDING,
                null);
    }

    static SingleTableRunContext recovery(
            String workspaceId,
            String executionId,
            DataSyncDefinitionSnapshotVO snapshot,
            int nextAttemptNo,
            int maxAttempts,
            int backoffSeconds,
            LocalDateTime nextRetryTime) {
        if (nextAttemptNo < 2 || maxAttempts < nextAttemptNo || nextRetryTime == null) {
            throw new IllegalArgumentException("invalid durable retry recovery state");
        }
        return new SingleTableRunContext(
                workspaceId,
                executionId,
                snapshot,
                nextAttemptNo,
                maxAttempts,
                Math.max(0, backoffSeconds),
                DataSyncInstanceStatus.RETRY_WAITING,
                nextRetryTime);
    }
}
