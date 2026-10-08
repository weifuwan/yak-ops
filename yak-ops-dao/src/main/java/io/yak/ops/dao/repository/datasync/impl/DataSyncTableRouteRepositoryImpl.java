package io.yak.ops.dao.repository.datasync.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncTableRouteEntity;
import io.yak.ops.dao.mapper.datasync.DataSyncTableRouteMapper;
import io.yak.ops.dao.repository.datasync.DataSyncTableRouteRepository;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 使用 MyBatis-Plus 实现 Workspace-scoped Data Sync Table Route 查询与写入。
 *
 * @author weifuwan
 * @since 2026-10-07
 */
@Repository
public class DataSyncTableRouteRepositoryImpl
        extends BaseRepositoryImpl<DataSyncTableRouteMapper, DataSyncTableRouteEntity>
        implements DataSyncTableRouteRepository {

    @Resource
    private DataSyncTableRouteMapper tableRouteMapper;

    @Override
    protected DataSyncTableRouteMapper mapper() {
        return tableRouteMapper;
    }

    @Override
    public Optional<DataSyncTableRouteEntity> queryById(String workspaceId, String id) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(id)) return Optional.empty();
        return Optional.ofNullable(tableRouteMapper.selectOne(Wrappers.<DataSyncTableRouteEntity>lambdaQuery()
                .eq(DataSyncTableRouteEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncTableRouteEntity::getId, id)));
    }

    @Override
    public List<DataSyncTableRouteEntity> queryByTask(String workspaceId, String taskId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(taskId)) return List.of();
        return tableRouteMapper.selectList(Wrappers.<DataSyncTableRouteEntity>lambdaQuery()
                .eq(DataSyncTableRouteEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncTableRouteEntity::getTaskId, taskId)
                .orderByAsc(DataSyncTableRouteEntity::getSortOrder)
                .orderByAsc(DataSyncTableRouteEntity::getId));
    }

    @Override
    public DataSyncTableRouteEntity update(String workspaceId, DataSyncTableRouteEntity entity) {
        if (StringUtils.isBlank(workspaceId) || entity == null || StringUtils.isBlank(entity.getId())) return null;
        int updated = tableRouteMapper.update(
                entity,
                Wrappers.<DataSyncTableRouteEntity>lambdaUpdate()
                        .eq(DataSyncTableRouteEntity::getWorkspaceId, workspaceId)
                        .eq(DataSyncTableRouteEntity::getId, entity.getId()));
        return updated > 0 ? entity : null;
    }

    @Override
    public int deleteById(String workspaceId, String id) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(id)) return 0;
        return tableRouteMapper.delete(Wrappers.<DataSyncTableRouteEntity>lambdaQuery()
                .eq(DataSyncTableRouteEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncTableRouteEntity::getId, id));
    }

    @Override
    public int deleteByTask(String workspaceId, String taskId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(taskId)) return 0;
        return tableRouteMapper.delete(Wrappers.<DataSyncTableRouteEntity>lambdaQuery()
                .eq(DataSyncTableRouteEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncTableRouteEntity::getTaskId, taskId));
    }
}
