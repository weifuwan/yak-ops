package io.yak.ops.dao.repository.datasync.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.mapper.datasync.DataSyncTaskMapper;
import io.yak.ops.dao.repository.datasync.DataSyncTaskPageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 使用 MyBatis-Plus 实现 Workspace-scoped 数据同步任务查询和名称唯一性校验。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Repository
public class DataSyncTaskRepositoryImpl extends BaseRepositoryImpl<DataSyncTaskMapper, DataSyncTaskEntity>
        implements DataSyncTaskRepository {

    @Resource
    private DataSyncTaskMapper taskMapper;

    @Override
    protected DataSyncTaskMapper mapper() {
        return taskMapper;
    }

    @Override
    public PageData<DataSyncTaskEntity> queryPage(String workspaceId, DataSyncTaskPageQuery query) {
        DataSyncTaskPageQuery condition =
                query == null ? new DataSyncTaskPageQuery(1, 10, null, null, null, null, null) : query;
        Page<DataSyncTaskEntity> page = Page.of(Math.max(1, condition.pageNo()), Math.max(1, condition.pageSize()));
        IPage<DataSyncTaskEntity> result = taskMapper.selectPage(
                page,
                queryWrapper(workspaceId, condition)
                        .orderByDesc(DataSyncTaskEntity::getUpdateTime)
                        .orderByDesc(DataSyncTaskEntity::getId));
        return new PageData<>(
                result.getRecords(), result.getTotal(), result.getPages(), result.getCurrent(), result.getSize());
    }

    @Override
    public Optional<DataSyncTaskEntity> queryById(String workspaceId, String id) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(id)) return Optional.empty();
        return Optional.ofNullable(taskMapper.selectOne(Wrappers.<DataSyncTaskEntity>lambdaQuery()
                .eq(DataSyncTaskEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncTaskEntity::getId, id)));
    }

    @Override
    public List<DataSyncTaskEntity> queryRealtimeDesiredRunning() {
        return taskMapper.selectList(Wrappers.<DataSyncTaskEntity>lambdaQuery()
                .eq(DataSyncTaskEntity::getSyncType, DataSyncType.REALTIME)
                .eq(DataSyncTaskEntity::getStatus, DataSyncTaskStatus.PUBLISHED)
                .eq(DataSyncTaskEntity::getDesiredState, DataSyncDesiredState.RUNNING)
                .orderByAsc(DataSyncTaskEntity::getWorkspaceId)
                .orderByAsc(DataSyncTaskEntity::getId));
    }

    @Override
    public DataSyncTaskEntity update(String workspaceId, DataSyncTaskEntity entity) {
        if (!StringUtils.hasText(workspaceId) || entity == null || !StringUtils.hasText(entity.getId())) return null;
        int updated = taskMapper.update(
                entity,
                Wrappers.<DataSyncTaskEntity>lambdaUpdate()
                        .eq(DataSyncTaskEntity::getWorkspaceId, workspaceId)
                        .eq(DataSyncTaskEntity::getId, entity.getId()));
        return updated > 0 ? entity : null;
    }

    @Override
    public int deleteById(String workspaceId, String id) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(id)) return 0;
        return taskMapper.delete(Wrappers.<DataSyncTaskEntity>lambdaQuery()
                .eq(DataSyncTaskEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncTaskEntity::getId, id));
    }

    @Override
    public boolean existsByName(String workspaceId, String name, String excludeId) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(name)) return false;
        Long count = taskMapper.selectCount(Wrappers.<DataSyncTaskEntity>lambdaQuery()
                .eq(DataSyncTaskEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncTaskEntity::getName, name)
                .ne(excludeId != null, DataSyncTaskEntity::getId, excludeId));
        return count != null && count > 0;
    }

    private LambdaQueryWrapper<DataSyncTaskEntity> queryWrapper(String workspaceId, DataSyncTaskPageQuery query) {
        LambdaQueryWrapper<DataSyncTaskEntity> wrapper =
                Wrappers.<DataSyncTaskEntity>lambdaQuery().eq(DataSyncTaskEntity::getWorkspaceId, workspaceId);
        if (StringUtils.hasText(query.keyword())) {
            wrapper.and(nested -> nested.like(DataSyncTaskEntity::getName, query.keyword())
                    .or()
                    .like(DataSyncTaskEntity::getSourceTable, query.keyword())
                    .or()
                    .like(DataSyncTaskEntity::getTargetTable, query.keyword()));
        }
        return wrapper.eq(query.syncType() != null, DataSyncTaskEntity::getSyncType, query.syncType())
                .eq(query.status() != null, DataSyncTaskEntity::getStatus, query.status())
                .eq(
                        StringUtils.hasText(query.sourceDataSourceId()),
                        DataSyncTaskEntity::getSourceDataSourceId,
                        query.sourceDataSourceId())
                .eq(
                        StringUtils.hasText(query.targetDataSourceId()),
                        DataSyncTaskEntity::getTargetDataSourceId,
                        query.targetDataSourceId());
    }
}
