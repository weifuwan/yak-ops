package io.yak.ops.dao.repository.datasync;

import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.dao.entity.datasync.DataSyncTableAttemptEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 按 Workspace + Table Execution 隔离 Attempt 历史及条件状态更新。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
public interface DataSyncTableAttemptRepository extends BaseRepository<DataSyncTableAttemptEntity> {

    List<DataSyncTableAttemptEntity> queryByTableExecution(String workspaceId, String tableExecutionId);

    boolean transition(
            String workspaceId,
            String id,
            DataSyncAttemptStatus expected,
            DataSyncAttemptStatus target,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Integer errorCode,
            String message);

    boolean updateMetrics(String workspaceId, String id, long readRows, long writeRows);

    int cancelActive(String workspaceId, String tableExecutionId, LocalDateTime finishedAt);

    int markActiveAsLost(
            String workspaceId, String tableExecutionId, LocalDateTime finishedAt, Integer errorCode, String message);

    int markActiveAsLost(LocalDateTime finishedAt, Integer errorCode, String message);
}
