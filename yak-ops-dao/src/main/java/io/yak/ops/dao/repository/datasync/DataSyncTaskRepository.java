package io.yak.ops.dao.repository.datasync;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.List;
import java.util.Optional;

/**
 * 定义 Workspace-scoped 数据同步任务定义持久化能力。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface DataSyncTaskRepository extends BaseRepository<DataSyncTaskEntity> {

    PageData<DataSyncTaskEntity> queryPage(String workspaceId, DataSyncTaskPageQuery query);

    Optional<DataSyncTaskEntity> queryById(String workspaceId, String id);

    List<DataSyncTaskEntity> queryRealtimeDesiredRunning();

    DataSyncTaskEntity update(String workspaceId, DataSyncTaskEntity entity);

    int deleteById(String workspaceId, String id);

    boolean existsByName(String workspaceId, String name, String excludeId);
}
