package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.mapper.task.DefinitionVersionMapper;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 追加并读取 Task Definition 历史版本；不允许更新已发布的快照。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Repository
public class DefinitionVersionRepositoryImpl implements DefinitionVersionRepository {

    @Resource
    private DefinitionVersionMapper versionMapper;

    @Override
    public DefinitionVersionEntity append(DefinitionVersionEntity version) {
        versionMapper.insert(version);
        return version;
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
