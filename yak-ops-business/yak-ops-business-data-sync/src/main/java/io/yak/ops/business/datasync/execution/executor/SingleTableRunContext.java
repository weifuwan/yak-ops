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
        LocalDateTime initialRetryTime) {}
