package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.mapper.task.DefinitionMapper;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Workspace-scoped Task Definition persistence. */
@Repository
public class DefinitionRepositoryImpl extends BaseRepositoryImpl<DefinitionMapper, DefinitionEntity>
        implements DefinitionRepository {
    @Resource
    private DefinitionMapper definitionMapper;

    @Override
    protected DefinitionMapper mapper() {
        return definitionMapper;
    }

    @Override
    public Optional<DefinitionEntity> queryById(String workspaceId, String id) {
        return Optional.ofNullable(definitionMapper.selectOne(Wrappers.<DefinitionEntity>lambdaQuery()
                .eq(DefinitionEntity::getWorkspaceId, workspaceId)
                .eq(DefinitionEntity::getId, id)
                .isNull(DefinitionEntity::getDeletedAt)));
    }

    @Override
    public Optional<DefinitionEntity> queryByIdIncludingDeleted(String workspaceId, String id) {
        return Optional.ofNullable(definitionMapper.selectOne(Wrappers.<DefinitionEntity>lambdaQuery()
                .eq(DefinitionEntity::getWorkspaceId, workspaceId)
                .eq(DefinitionEntity::getId, id)));
    }

    @Override
    public Optional<DefinitionEntity> queryByIdForUpdate(String workspaceId, String id) {
        return Optional.ofNullable(definitionMapper.selectOne(Wrappers.<DefinitionEntity>lambdaQuery()
                .eq(DefinitionEntity::getWorkspaceId, workspaceId)
                .eq(DefinitionEntity::getId, id)
                .isNull(DefinitionEntity::getDeletedAt)
                .last("FOR UPDATE")));
    }

    @Override
    public Optional<DefinitionEntity> queryByIdIncludingDeletedForUpdate(String workspaceId, String id) {
        return Optional.ofNullable(definitionMapper.selectOne(Wrappers.<DefinitionEntity>lambdaQuery()
                .eq(DefinitionEntity::getWorkspaceId, workspaceId)
                .eq(DefinitionEntity::getId, id)
                .last("FOR UPDATE")));
    }

    @Override
    public PageData<DefinitionEntity> queryPage(DefinitionPageQuery filter) {
        var page = definitionMapper.selectDefinitionPage(new Page<>(filter.pageNo(), filter.pageSize()), filter);
        return new PageData<>(page.getRecords(), page.getTotal(), page.getPages(), page.getCurrent(), page.getSize());
    }

    @Override
    public DefinitionEntity update(String workspaceId, DefinitionEntity entity) {
        int count = definitionMapper.update(
                entity,
                Wrappers.<DefinitionEntity>lambdaUpdate()
                        .eq(DefinitionEntity::getWorkspaceId, workspaceId)
                        .eq(DefinitionEntity::getId, entity.getId())
                        .isNull(DefinitionEntity::getDeletedAt));
        return count == 1 ? entity : null;
    }

    @Override
    public boolean softDelete(String workspaceId, String id, LocalDateTime at, String operator) {
        return definitionMapper.update(
                        null,
                        Wrappers.<DefinitionEntity>lambdaUpdate()
                                .eq(DefinitionEntity::getWorkspaceId, workspaceId)
                                .eq(DefinitionEntity::getId, id)
                                .isNull(DefinitionEntity::getDeletedAt)
                                .set(DefinitionEntity::getDeletedAt, at)
                                .set(DefinitionEntity::getUpdateTime, at)
                                .set(DefinitionEntity::getUpdateBy, operator))
                == 1;
    }

    @Override
    public boolean existsByName(String workspaceId, String name, String excludeId) {
        return definitionMapper.selectCount(Wrappers.<DefinitionEntity>lambdaQuery()
                        .eq(DefinitionEntity::getWorkspaceId, workspaceId)
                        .eq(DefinitionEntity::getName, name)
                        .isNull(DefinitionEntity::getDeletedAt)
                        .ne(excludeId != null, DefinitionEntity::getId, excludeId))
                > 0;
    }
}
