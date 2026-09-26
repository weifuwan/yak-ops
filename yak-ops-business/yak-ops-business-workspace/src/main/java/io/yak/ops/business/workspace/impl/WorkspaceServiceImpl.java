package io.yak.ops.business.workspace.impl;

import io.yak.ops.business.workspace.WorkspaceService;
import io.yak.ops.business.workspace.enums.WorkspaceErrorCode;
import io.yak.ops.business.workspace.exception.WorkspaceException;
import io.yak.ops.common.bean.dto.workspace.WorkspaceDTO;
import io.yak.ops.common.bean.vo.workspace.WorkspaceMemberVO;
import io.yak.ops.common.bean.vo.workspace.WorkspaceVO;
import io.yak.ops.common.enums.workspace.WorkspaceRole;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.workspace.WorkspaceEntity;
import io.yak.ops.dao.entity.workspace.WorkspaceMemberEntity;
import io.yak.ops.dao.repository.workspace.WorkspaceEntityRepository;
import io.yak.ops.dao.repository.workspace.WorkspaceMemberEntityRepository;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实现 Workspace 创建、当前用户 Workspace 查询和成员关系校验。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceServiceImpl.class);

    @Resource
    private WorkspaceEntityRepository workspaceRepository;

    @Resource
    private WorkspaceMemberEntityRepository workspaceMemberRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceVO createWorkspace(WorkspaceDTO dto, String userId) {
        String operatorId = requireUserId(userId);
        String name = dto == null ? null : StringUtils.trimToNull(dto.getName());
        if (name == null) throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE);

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setName(name);
        workspace.setDescription(dto == null ? null : StringUtils.trimToNull(dto.getDescription()));
        workspace.initCreate(operatorId);
        workspaceRepository.add(workspace);

        WorkspaceMemberEntity owner = new WorkspaceMemberEntity();
        owner.setWorkspaceId(workspace.getId());
        owner.setUserId(operatorId);
        owner.setRole(WorkspaceRole.OWNER);
        owner.initCreate(operatorId);
        workspaceMemberRepository.add(owner);

        LOG.info("工作空间创建完成，workspaceId={}, ownerUserId={}", workspace.getId(), operatorId);
        return toWorkspaceVO(workspace, owner.getRole());
    }

    @Override
    public List<WorkspaceVO> queryMyWorkspaces(String userId) {
        String currentUserId = requireUserId(userId);
        List<WorkspaceMemberEntity> memberships = workspaceMemberRepository.queryByUserId(currentUserId);
        if (memberships.isEmpty()) return List.of();

        Map<String, WorkspaceRole> roles = new LinkedHashMap<>();
        for (WorkspaceMemberEntity membership : memberships) {
            roles.put(membership.getWorkspaceId(), membership.getRole());
        }
        return workspaceRepository.queryByIds(List.copyOf(roles.keySet())).stream()
                .map(workspace -> toWorkspaceVO(workspace, roles.get(workspace.getId())))
                .toList();
    }

    @Override
    public WorkspaceVO queryWorkspace(String workspaceId, String userId) {
        WorkspaceMemberEntity membership = requireMember(workspaceId, userId);
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        return toWorkspaceVO(workspace, membership.getRole());
    }

    @Override
    public List<WorkspaceMemberVO> queryWorkspaceMembers(String workspaceId, String userId) {
        requireMember(workspaceId, userId);
        requireWorkspace(workspaceId);
        return workspaceMemberRepository.queryByWorkspaceId(workspaceId).stream()
                .map(this::toWorkspaceMemberVO)
                .toList();
    }

    @Override
    public boolean isMember(String workspaceId, String userId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(userId)) return false;
        return workspaceMemberRepository.queryMembership(workspaceId, userId).isPresent()
                && workspaceRepository.queryById(workspaceId).isPresent();
    }

    private WorkspaceEntity requireWorkspace(String workspaceId) {
        String id = StringUtils.trimToNull(workspaceId);
        if (id == null) throw new WorkspaceException(WorkspaceErrorCode.NOT_FOUND);
        return workspaceRepository.queryById(id).orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.NOT_FOUND));
    }

    private WorkspaceMemberEntity requireMember(String workspaceId, String userId) {
        String id = StringUtils.trimToNull(workspaceId);
        String currentUserId = requireUserId(userId);
        if (id == null) throw new WorkspaceException(WorkspaceErrorCode.ACCESS_DENIED);
        return workspaceMemberRepository
                .queryMembership(id, currentUserId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.ACCESS_DENIED));
    }

    private String requireUserId(String userId) {
        String id = StringUtils.trimToNull(userId);
        if (id == null) throw new WorkspaceException(WorkspaceErrorCode.ACCESS_DENIED);
        return id;
    }

    private WorkspaceVO toWorkspaceVO(WorkspaceEntity source, WorkspaceRole role) {
        WorkspaceVO target = new WorkspaceVO();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setRole(role);
        target.setRoleName(role == null ? null : role.getDisplayName());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    private WorkspaceMemberVO toWorkspaceMemberVO(WorkspaceMemberEntity source) {
        WorkspaceMemberVO target = new WorkspaceMemberVO();
        target.setUserId(source.getUserId());
        target.setRole(source.getRole());
        target.setRoleName(source.getRole() == null ? null : source.getRole().getDisplayName());
        target.setJoinedAt(source.getCreateTime());
        return target;
    }
}
