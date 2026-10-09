package io.yak.ops.dao.repository.datasync;

import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定义 Workspace-scoped Data Sync Attempt 持久化与状态迁移能力。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
public interface DataSyncAttemptRepository extends BaseRepository<DataSyncAttemptEntity> {

    List<DataSyncAttemptEntity> queryByExecution(String workspaceId, String executionId);

    boolean updateMetrics(String workspaceId, String id, long readRows, long writeRows);

    boolean transitionStatus(
            String workspaceId,
            String id,
            DataSyncAttemptStatus expectedStatus,
            DataSyncAttemptStatus targetStatus,
            LocalDateTime startTime,
            LocalDateTime finishTime,
            Integer errorCode,
            String errorMessage);

    int cancelActiveByExecution(String workspaceId, String executionId, LocalDateTime finishTime);

    int markActiveAsLost(LocalDateTime finishTime, Integer errorCode, String errorMessage);
}
