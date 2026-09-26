package io.yak.ops.dao.repository.workspace.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.dao.entity.workspace.WorkspaceEntity;
import io.yak.ops.dao.mapper.workspace.WorkspaceMapper;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import io.yak.ops.dao.repository.workspace.WorkspaceEntityRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 实现 Workspace 持久化查询。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Repository
public class WorkspaceEntityRepositoryImpl extends BaseRepositoryImpl<WorkspaceMapper, WorkspaceEntity>
        implements WorkspaceEntityRepository {

    @Resource
    private WorkspaceMapper workspaceMapper;

    @Override
    protected WorkspaceMapper mapper() {
        return workspaceMapper;
    }

    @Override
    public List<WorkspaceEntity> queryByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return workspaceMapper.selectList(Wrappers.<WorkspaceEntity>lambdaQuery()
                .in(WorkspaceEntity::getId, ids)
                .orderByDesc(WorkspaceEntity::getUpdateTime)
                .orderByDesc(WorkspaceEntity::getId));
    }
}
