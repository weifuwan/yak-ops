package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.task.DefinitionPublicationEntity;
import io.yak.ops.dao.mapper.task.DefinitionPublicationMapper;
import io.yak.ops.dao.repository.task.DefinitionPublicationRepository;
import jakarta.annotation.Resource;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Persists publication receipts without changing immutable historical rows. */
@Repository
public class DefinitionPublicationRepositoryImpl implements DefinitionPublicationRepository {
    @Resource
    private DefinitionPublicationMapper mapper;

    @Override
    public Optional<DefinitionPublicationEntity> findByRequest(
            String workspaceId, String definitionId, String requestId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<DefinitionPublicationEntity>lambdaQuery()
                .eq(DefinitionPublicationEntity::getWorkspaceId, workspaceId)
                .eq(DefinitionPublicationEntity::getDefinitionId, definitionId)
                .eq(DefinitionPublicationEntity::getRequestId, requestId)));
    }

    @Override
    public void append(DefinitionPublicationEntity publication) {
        mapper.insert(publication);
    }

    @Override
    public PageData<DefinitionPublicationEntity> queryPage(
            String workspaceId, String definitionId, int pageNo, int pageSize) {
        Page<DefinitionPublicationEntity> page = mapper.selectPage(
                new Page<>(pageNo, pageSize),
                Wrappers.<DefinitionPublicationEntity>lambdaQuery()
                        .eq(DefinitionPublicationEntity::getWorkspaceId, workspaceId)
                        .eq(DefinitionPublicationEntity::getDefinitionId, definitionId)
                        .orderByDesc(DefinitionPublicationEntity::getCreateTime, DefinitionPublicationEntity::getId));
        return new PageData<>(page.getRecords(), page.getTotal(), page.getPages(), page.getCurrent(), page.getSize());
    }
}
