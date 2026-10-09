package io.yak.ops.dao.repository.datasync;

import io.yak.ops.dao.entity.datasync.DataSyncExecutionEventEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.List;

/**
 * 定义 Workspace-scoped Data Sync Execution 产品事件持久化能力。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
public interface DataSyncExecutionEventRepository extends BaseRepository<DataSyncExecutionEventEntity> {

    List<DataSyncExecutionEventEntity> queryByExecution(String workspaceId, String executionId);
}
