package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.mapper.task.DefinitionVersionMapper;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 保存并读取任务定义历史快照，不提供更新历史快照的业务入口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Repository
public class DefinitionVersionRepositoryImpl
        extends BaseRepositoryImpl<DefinitionVersionMapper, DefinitionVersionEntity>
        implements DefinitionVersionRepository {

    @Resource
    private DefinitionVersionMapper versionMapper;

    @Override
    protected DefinitionVersionMapper mapper() {
        return versionMapper;
    }

    @Override
    public List<DefinitionVersionEntity> queryByDefinition(String workspaceId, String definitionId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(definitionId)) return List.of();
        return versionMapper.selectList(Wrappers.<DefinitionVersionEntity>lambdaQuery()
                .eq(DefinitionVersionEntity::getWorkspaceId, workspaceId)
                .eq(DefinitionVersionEntity::getDefinitionId, definitionId)
                .orderByDesc(DefinitionVersionEntity::getVersion));
    }
}
