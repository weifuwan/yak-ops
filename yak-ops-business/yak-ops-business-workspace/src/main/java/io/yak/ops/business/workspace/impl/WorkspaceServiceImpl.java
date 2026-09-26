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
 * 实现 Workspace 创建、发现、成员关系查询和成员管理。
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
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceMemberVO addWorkspaceMember(
            String workspaceId, String targetUserId, WorkspaceRole role, String operatorUserId) {
        requireWorkspace(workspaceId);
        WorkspaceMemberEntity operator = requireManager(workspaceId, operatorUserId);
        String memberUserId = requireUserId(targetUserId);
        WorkspaceRole targetRole = requireRole(role);
        requireOwnerForOwnerRole(operator.getRole(), targetRole);

        if (workspaceMemberRepository.queryMembership(workspaceId, memberUserId).isPresent()) {
            throw new WorkspaceException(WorkspaceErrorCode.MEMBER_ALREADY_EXISTS);
        }

        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspaceId);
        member.setUserId(memberUserId);
        member.setRole(targetRole);
        member.initCreate(requireUserId(operatorUserId));
        workspaceMemberRepository.add(member);

        LOG.info(
                "工作空间成员添加完成，workspaceId={}, userId={}, role={}, operatorUserId={}",
                workspaceId,
                memberUserId,
                targetRole,
                operatorUserId);
        return toWorkspaceMemberVO(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceMemberVO updateWorkspaceMemberRole(
            String workspaceId, String targetUserId, WorkspaceRole role, String operatorUserId) {
        requireWorkspace(workspaceId);
        WorkspaceMemberEntity operator = requireManager(workspaceId, operatorUserId);
        WorkspaceMemberEntity target = requireTargetMember(workspaceId, targetUserId);
        WorkspaceRole targetRole = requireRole(role);

        if (target.getRole() == targetRole) return toWorkspaceMemberVO(target);
        requireOwnerForOwnerMembership(operator.getRole(), target.getRole(), targetRole);
        ensureOwnerRemains(workspaceId, target.getRole(), targetRole);

        target.setRole(targetRole);
        target.initUpdate(requireUserId(operatorUserId));
        workspaceMemberRepository.update(target);

        LOG.info(
                "工作空间成员角色更新完成，workspaceId={}, userId={}, role={}, operatorUserId={}",
                workspaceId,
                target.getUserId(),
                targetRole,
                operatorUserId);
        return toWorkspaceMemberVO(target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeWorkspaceMember(String workspaceId, String targetUserId, String operatorUserId) {
        requireWorkspace(workspaceId);
        WorkspaceMemberEntity operator = requireManager(workspaceId, operatorUserId);
        WorkspaceMemberEntity target = requireTargetMember(workspaceId, targetUserId);

        if (target.getRole() == WorkspaceRole.OWNER && operator.getRole() != WorkspaceRole.OWNER) {
            throw new WorkspaceException(WorkspaceErrorCode.ROLE_OPERATION_DENIED);
        }
        ensureOwnerRemains(workspaceId, target.getRole(), null);

        if (workspaceMemberRepository.deleteMembership(workspaceId, target.getUserId()) <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.MEMBER_NOT_FOUND);
        }

        LOG.info(
                "工作空间成员移除完成，workspaceId={}, userId={}, operatorUserId={}",
                workspaceId,
                target.getUserId(),
                operatorUserId);
        return true;
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
        return workspaceRepository
                .queryById(id)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.NOT_FOUND));
    }

    private WorkspaceMemberEntity requireMember(String workspaceId, String userId) {
        String id = StringUtils.trimToNull(workspaceId);
        String currentUserId = requireUserId(userId);
        if (id == null) throw new WorkspaceException(WorkspaceErrorCode.ACCESS_DENIED);
        return workspaceMemberRepository
                .queryMembership(id, currentUserId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.ACCESS_DENIED));
    }

    private WorkspaceMemberEntity requireTargetMember(String workspaceId, String userId) {
        String id = StringUtils.trimToNull(workspaceId);
        String targetUserId = requireUserId(userId);
        if (id == null) throw new WorkspaceException(WorkspaceErrorCode.MEMBER_NOT_FOUND);
        return workspaceMemberRepository
                .queryMembership(id, targetUserId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.MEMBER_NOT_FOUND));
    }

    private WorkspaceMemberEntity requireManager(String workspaceId, String userId) {
        WorkspaceMemberEntity membership = workspaceMemberRepository
                .queryMembership(workspaceId, requireUserId(userId))
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.ACCESS_DENIED));
        if (membership.getRole() != WorkspaceRole.OWNER && membership.getRole() != WorkspaceRole.ADMIN) {
            throw new WorkspaceException(WorkspaceErrorCode.ACCESS_DENIED);
        }
        return membership;
    }

    private WorkspaceRole requireRole(WorkspaceRole role) {
        if (role == null) throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE);
        return role;
    }

    private void requireOwnerForOwnerRole(WorkspaceRole operatorRole, WorkspaceRole targetRole) {
        if (targetRole == WorkspaceRole.OWNER && operatorRole != WorkspaceRole.OWNER) {
            throw new WorkspaceException(WorkspaceErrorCode.ROLE_OPERATION_DENIED);
        }
    }

    private void requireOwnerForOwnerMembership(
            WorkspaceRole operatorRole, WorkspaceRole currentRole, WorkspaceRole targetRole) {
        if ((currentRole == WorkspaceRole.OWNER || targetRole == WorkspaceRole.OWNER)
                && operatorRole != WorkspaceRole.OWNER) {
            throw new WorkspaceException(WorkspaceErrorCode.ROLE_OPERATION_DENIED);
        }
    }

    private void ensureOwnerRemains(String workspaceId, WorkspaceRole currentRole, WorkspaceRole targetRole) {
        if (currentRole != WorkspaceRole.OWNER || targetRole == WorkspaceRole.OWNER) return;
        if (workspaceMemberRepository.countByRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
            throw new WorkspaceException(WorkspaceErrorCode.LAST_OWNER_REQUIRED);
        }
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
