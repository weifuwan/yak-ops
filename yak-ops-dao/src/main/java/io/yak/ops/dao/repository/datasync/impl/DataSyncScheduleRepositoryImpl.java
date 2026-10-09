package io.yak.ops.dao.repository.datasync.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.mapper.datasync.DataSyncScheduleMapper;
import io.yak.ops.dao.repository.datasync.DataSyncScheduleRepository;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 使用 MyBatis-Plus 实现 Workspace-scoped 数据同步调度查询和启用列表读取。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Repository
public class DataSyncScheduleRepositoryImpl extends BaseRepositoryImpl<DataSyncScheduleMapper, DataSyncScheduleEntity>
        implements DataSyncScheduleRepository {

    @Resource
    private DataSyncScheduleMapper scheduleMapper;

    @Override
    protected DataSyncScheduleMapper mapper() {
        return scheduleMapper;
    }

    @Override
    public Optional<DataSyncScheduleEntity> queryById(String workspaceId, String id) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(id)) return Optional.empty();
        return Optional.ofNullable(scheduleMapper.selectOne(Wrappers.<DataSyncScheduleEntity>lambdaQuery()
                .eq(DataSyncScheduleEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncScheduleEntity::getId, id)));
    }

    @Override
    public Optional<DataSyncScheduleEntity> queryByTask(String workspaceId, String taskId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(taskId)) return Optional.empty();
        return Optional.ofNullable(scheduleMapper.selectOne(Wrappers.<DataSyncScheduleEntity>lambdaQuery()
                .eq(DataSyncScheduleEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncScheduleEntity::getTaskId, taskId)));
    }

    @Override
    public List<DataSyncScheduleEntity> queryByTasks(String workspaceId, List<String> taskIds) {
        if (StringUtils.isBlank(workspaceId) || taskIds == null || taskIds.isEmpty()) return List.of();
        return scheduleMapper.selectList(Wrappers.<DataSyncScheduleEntity>lambdaQuery()
                .eq(DataSyncScheduleEntity::getWorkspaceId, workspaceId)
                .in(DataSyncScheduleEntity::getTaskId, taskIds));
    }

    @Override
    public List<DataSyncScheduleEntity> queryEnabled() {
        return scheduleMapper.selectList(Wrappers.<DataSyncScheduleEntity>lambdaQuery()
                .eq(DataSyncScheduleEntity::getEnabled, true)
                .orderByAsc(DataSyncScheduleEntity::getId));
    }

    @Override
    public DataSyncScheduleEntity update(String workspaceId, DataSyncScheduleEntity entity) {
        if (StringUtils.isBlank(workspaceId) || entity == null || StringUtils.isBlank(entity.getId())) return null;
        int updated = scheduleMapper.update(
                entity,
                Wrappers.<DataSyncScheduleEntity>lambdaUpdate()
                        .eq(DataSyncScheduleEntity::getWorkspaceId, workspaceId)
                        .eq(DataSyncScheduleEntity::getId, entity.getId()));
        return updated > 0 ? entity : null;
    }

    @Override
    public int deleteByTask(String workspaceId, String taskId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(taskId)) return 0;
        return scheduleMapper.delete(Wrappers.<DataSyncScheduleEntity>lambdaQuery()
                .eq(DataSyncScheduleEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncScheduleEntity::getTaskId, taskId));
    }
}
