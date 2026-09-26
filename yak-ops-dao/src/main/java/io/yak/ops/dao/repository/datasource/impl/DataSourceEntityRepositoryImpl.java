package io.yak.ops.dao.repository.datasource.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.mapper.datasource.DataSourceMapper;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import io.yak.ops.dao.repository.datasource.DataSourcePageQuery;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import jakarta.annotation.Resource;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 使用 MyBatis-Plus 实现 Workspace-scoped 数据源查询、更新、删除和名称校验。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Repository
public class DataSourceEntityRepositoryImpl extends BaseRepositoryImpl<DataSourceMapper, DataSourceEntity>
        implements DataSourceEntityRepository {

    @Resource
    private DataSourceMapper dataSourceMapper;

    @Override
    protected DataSourceMapper mapper() {
        return dataSourceMapper;
    }

    @Override
    public PageData<DataSourceEntity> queryPage(String workspaceId, DataSourcePageQuery query) {
        DataSourcePageQuery condition =
                query == null ? new DataSourcePageQuery(1, 10, null, null, null, null, null) : query;
        Page<DataSourceEntity> page = Page.of(Math.max(1, condition.pageNo()), Math.max(1, condition.pageSize()));
        IPage<DataSourceEntity> result = dataSourceMapper.selectPage(
                page,
                queryWrapper(workspaceId, condition)
                        .orderByDesc(DataSourceEntity::getUpdateTime)
                        .orderByDesc(DataSourceEntity::getId));
        return new PageData<>(
                result.getRecords(), result.getTotal(), result.getPages(), result.getCurrent(), result.getSize());
    }

    @Override
    public Optional<DataSourceEntity> queryById(String workspaceId, String id) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(id)) return Optional.empty();
        return Optional.ofNullable(dataSourceMapper.selectOne(Wrappers.<DataSourceEntity>lambdaQuery()
                .eq(DataSourceEntity::getWorkspaceId, workspaceId)
                .eq(DataSourceEntity::getId, id)));
    }

    @Override
    public DataSourceEntity update(String workspaceId, DataSourceEntity entity) {
        if (!StringUtils.hasText(workspaceId) || entity == null || !StringUtils.hasText(entity.getId())) return null;
        int updated = dataSourceMapper.update(
                entity,
                Wrappers.<DataSourceEntity>lambdaUpdate()
                        .eq(DataSourceEntity::getWorkspaceId, workspaceId)
                        .eq(DataSourceEntity::getId, entity.getId()));
        return updated > 0 ? entity : null;
    }

    @Override
    public int deleteById(String workspaceId, String id) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(id)) return 0;
        return dataSourceMapper.delete(Wrappers.<DataSourceEntity>lambdaQuery()
                .eq(DataSourceEntity::getWorkspaceId, workspaceId)
                .eq(DataSourceEntity::getId, id));
    }

    @Override
    public boolean existsByName(String workspaceId, String name, String excludeId) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(name)) return false;
        Long count = dataSourceMapper.selectCount(Wrappers.<DataSourceEntity>lambdaQuery()
                .eq(DataSourceEntity::getWorkspaceId, workspaceId)
                .eq(DataSourceEntity::getName, name)
                .ne(excludeId != null, DataSourceEntity::getId, excludeId));
        return count != null && count > 0;
    }

    private LambdaQueryWrapper<DataSourceEntity> queryWrapper(String workspaceId, DataSourcePageQuery query) {
        LambdaQueryWrapper<DataSourceEntity> wrapper =
                Wrappers.<DataSourceEntity>lambdaQuery().eq(DataSourceEntity::getWorkspaceId, workspaceId);
        if (StringUtils.hasText(query.keyword())) {
            wrapper.and(nested -> nested.like(DataSourceEntity::getName, query.keyword())
                    .or()
                    .like(DataSourceEntity::getJdbcUrl, query.keyword()));
        }
        return wrapper.like(StringUtils.hasText(query.name()), DataSourceEntity::getName, query.name())
                .eq(query.dbType() != null, DataSourceEntity::getDbType, query.dbType())
                .eq(query.environment() != null, DataSourceEntity::getEnvironment, query.environment())
                .eq(query.connStatus() != null, DataSourceEntity::getConnStatus, query.connStatus());
    }
}
