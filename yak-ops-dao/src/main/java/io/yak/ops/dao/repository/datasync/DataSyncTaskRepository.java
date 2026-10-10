package io.yak.ops.dao.repository.datasync;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import java.util.List;
import java.util.Optional;

/**
 * 定义 Workspace-scoped 数据同步任务定义持久化能力。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface DataSyncTaskRepository { 
    /** 新任务定义与插件配置在同一事务内创建，返回已初始化的投影。 */
    DataSyncTaskEntity add(DataSyncTaskEntity entity);


    PageData<DataSyncTaskEntity> queryPage(String workspaceId, DataSyncTaskPageQuery query);

    Optional<DataSyncTaskEntity> queryById(String workspaceId, String id);

    List<DataSyncTaskEntity> queryRealtimeDesiredRunning();

    DataSyncTaskEntity update(String workspaceId, DataSyncTaskEntity entity);

    int deleteById(String workspaceId, String id);

    boolean existsByName(String workspaceId, String name, String excludeId);
}
