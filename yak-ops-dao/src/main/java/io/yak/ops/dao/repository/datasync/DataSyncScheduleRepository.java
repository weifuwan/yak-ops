package io.yak.ops.dao.repository.datasync;

import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.List;
import java.util.Optional;

/**
 * 定义 Workspace-scoped 数据同步调度持久化能力。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
public interface DataSyncScheduleRepository extends BaseRepository<DataSyncScheduleEntity> {

    Optional<DataSyncScheduleEntity> queryById(String workspaceId, String id);

    Optional<DataSyncScheduleEntity> queryByTask(String workspaceId, String taskId);

    List<DataSyncScheduleEntity> queryByTasks(String workspaceId, List<String> taskIds);

    List<DataSyncScheduleEntity> queryEnabled();

    DataSyncScheduleEntity update(String workspaceId, DataSyncScheduleEntity entity);

    int deleteByTask(String workspaceId, String taskId);
}
