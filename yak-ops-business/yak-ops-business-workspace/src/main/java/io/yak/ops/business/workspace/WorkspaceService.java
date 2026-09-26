package io.yak.ops.business.workspace;

import io.yak.ops.common.bean.dto.workspace.WorkspaceDTO;
import io.yak.ops.common.bean.vo.workspace.WorkspaceMemberVO;
import io.yak.ops.common.bean.vo.workspace.WorkspaceVO;
import io.yak.ops.common.enums.workspace.WorkspaceRole;
import java.util.List;

/**
 * 定义 Workspace 创建、发现和成员管理的稳定业务边界。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
public interface WorkspaceService {

    WorkspaceVO createWorkspace(WorkspaceDTO dto, String userId);

    List<WorkspaceVO> queryMyWorkspaces(String userId);

    WorkspaceVO queryWorkspace(String workspaceId, String userId);

    List<WorkspaceMemberVO> queryWorkspaceMembers(String workspaceId, String userId);

    WorkspaceMemberVO addWorkspaceMember(
            String workspaceId, String targetUserId, WorkspaceRole role, String operatorUserId);

    WorkspaceMemberVO updateWorkspaceMemberRole(
            String workspaceId, String targetUserId, WorkspaceRole role, String operatorUserId);

    boolean removeWorkspaceMember(String workspaceId, String targetUserId, String operatorUserId);

    boolean isMember(String workspaceId, String userId);
}
