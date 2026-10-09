package io.yak.ops.dao.repository.datasync;

import io.yak.ops.dao.entity.datasync.DataSyncTableRouteEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.List;
import java.util.Optional;

/**
 * 定义 Workspace-scoped Data Sync Table Route 持久化能力。
 *
 * @author weifuwan
 * @since 2026-10-07
 */
public interface DataSyncTableRouteRepository extends BaseRepository<DataSyncTableRouteEntity> {

    Optional<DataSyncTableRouteEntity> queryById(String workspaceId, String id);

    List<DataSyncTableRouteEntity> queryByTask(String workspaceId, String taskId);

    DataSyncTableRouteEntity update(String workspaceId, DataSyncTableRouteEntity entity);

    /** 删除指定 Workspace 与 Route ID 下的定义，不影响历史 Table Execution。 */
    int deleteById(String workspaceId, String id);

    int deleteByTask(String workspaceId, String taskId);
}
