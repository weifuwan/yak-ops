package io.yak.ops.dao.repository.workspace.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.enums.workspace.WorkspaceRole;
import io.yak.ops.dao.entity.workspace.WorkspaceMemberEntity;
import io.yak.ops.dao.mapper.workspace.WorkspaceMemberMapper;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import io.yak.ops.dao.repository.workspace.WorkspaceMemberEntityRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 实现 Workspace 成员关系查询与维护。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Repository
public class WorkspaceMemberEntityRepositoryImpl
        extends BaseRepositoryImpl<WorkspaceMemberMapper, WorkspaceMemberEntity>
        implements WorkspaceMemberEntityRepository {

    @Resource
    private WorkspaceMemberMapper workspaceMemberMapper;

    @Override
    protected WorkspaceMemberMapper mapper() {
        return workspaceMemberMapper;
    }

    @Override
    public Optional<WorkspaceMemberEntity> queryMembership(String workspaceId, String userId) {
        return Optional.ofNullable(workspaceMemberMapper.selectOne(Wrappers.<WorkspaceMemberEntity>lambdaQuery()
                .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)
                .eq(WorkspaceMemberEntity::getUserId, userId)));
    }

    @Override
    public List<WorkspaceMemberEntity> queryByUserId(String userId) {
        return workspaceMemberMapper.selectList(Wrappers.<WorkspaceMemberEntity>lambdaQuery()
                .eq(WorkspaceMemberEntity::getUserId, userId)
                .orderByDesc(WorkspaceMemberEntity::getCreateTime)
                .orderByDesc(WorkspaceMemberEntity::getId));
    }

    @Override
    public List<WorkspaceMemberEntity> queryByWorkspaceId(String workspaceId) {
        return workspaceMemberMapper.selectList(Wrappers.<WorkspaceMemberEntity>lambdaQuery()
                .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)
                .orderByAsc(WorkspaceMemberEntity::getCreateTime)
                .orderByAsc(WorkspaceMemberEntity::getId));
    }

    @Override
    public long countByRole(String workspaceId, WorkspaceRole role) {
        Long count = workspaceMemberMapper.selectCount(Wrappers.<WorkspaceMemberEntity>lambdaQuery()
                .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)
                .eq(WorkspaceMemberEntity::getRole, role));
        return count == null ? 0L : count;
    }

    @Override
    public int deleteMembership(String workspaceId, String userId) {
        return workspaceMemberMapper.delete(Wrappers.<WorkspaceMemberEntity>lambdaQuery()
                .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)
                .eq(WorkspaceMemberEntity::getUserId, userId));
    }
}
