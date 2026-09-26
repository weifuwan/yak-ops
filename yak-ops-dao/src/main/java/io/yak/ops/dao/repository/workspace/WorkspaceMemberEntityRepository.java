package io.yak.ops.dao.repository.workspace;

import io.yak.ops.dao.entity.workspace.WorkspaceMemberEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.List;
import java.util.Optional;

/**
 * Workspace 成员关系持久化边界。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
public interface WorkspaceMemberEntityRepository extends BaseRepository<WorkspaceMemberEntity> {

    Optional<WorkspaceMemberEntity> queryMembership(String workspaceId, String userId);

    List<WorkspaceMemberEntity> queryByUserId(String userId);

    List<WorkspaceMemberEntity> queryByWorkspaceId(String workspaceId);
}
