package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.mapper.task.DefinitionMapper;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 持久化唯一的通用 Task Definition 身份、状态和版本。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
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
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(id)) return Optional.empty();
        return Optional.ofNullable(definitionMapper.selectOne(Wrappers.<DefinitionEntity>lambdaQuery()
                .eq(DefinitionEntity::getWorkspaceId, workspaceId)
                .eq(DefinitionEntity::getId, id)));
    }

    @Override
    public List<DefinitionEntity> queryByIds(String workspaceId, List<String> ids) {
        if (StringUtils.isBlank(workspaceId) || ids == null || ids.isEmpty()) return List.of();
        return definitionMapper.selectList(Wrappers.<DefinitionEntity>lambdaQuery()
                .eq(DefinitionEntity::getWorkspaceId, workspaceId)
                .in(DefinitionEntity::getId, ids));
    }

    @Override
    public List<DefinitionEntity> queryByIdsForRecovery(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return definitionMapper.selectBatchIds(ids);
    }

    @Override
    public DefinitionEntity update(String workspaceId, DefinitionEntity entity) {
        if (StringUtils.isBlank(workspaceId) || entity == null || StringUtils.isBlank(entity.getId())) return null;
        int changed = definitionMapper.update(
                entity, Wrappers.<DefinitionEntity>lambdaUpdate()
                        .eq(DefinitionEntity::getWorkspaceId, workspaceId)
                        .eq(DefinitionEntity::getId, entity.getId()));
        return changed > 0 ? entity : null;
    }

    @Override
    public int deleteById(String workspaceId, String id) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(id)) return 0;
        return definitionMapper.delete(Wrappers.<DefinitionEntity>lambdaQuery()
                .eq(DefinitionEntity::getWorkspaceId, workspaceId)
                .eq(DefinitionEntity::getId, id));
    }

    @Override
    public boolean existsByName(String workspaceId, String name, String excludeId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(name)) return false;
        return definitionMapper.selectCount(Wrappers.<DefinitionEntity>lambdaQuery()
                        .eq(DefinitionEntity::getWorkspaceId, workspaceId)
                        .eq(DefinitionEntity::getName, name)
                        .ne(StringUtils.isNotBlank(excludeId), DefinitionEntity::getId, excludeId))
                > 0;
    }
}
