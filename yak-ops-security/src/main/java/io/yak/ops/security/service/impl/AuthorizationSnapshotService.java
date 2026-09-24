package io.yak.ops.security.service.impl;

import io.yak.ops.security.common.constant.SecurityPermissionCode;
import io.yak.ops.security.common.entity.Permission;
import io.yak.ops.security.context.AuthorizationSnapshot;
import io.yak.ops.security.dao.PermissionDao;
import io.yak.ops.security.service.PermissionCache;
import io.yak.ops.security.service.RolePermissionService;
import io.yak.ops.security.service.UserProjectService;
import io.yak.ops.security.service.UserRoleService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 统一加载并缓存当前用户的角色、权限、菜单和项目授权事实。
 */
@Service
public class AuthorizationSnapshotService {

  private final PermissionCache permissionCache;
  private final UserRoleService userRoleService;
  private final RolePermissionService rolePermissionService;
  private final PermissionDao permissionDao;
  private final UserMenuGrantService userMenuGrantService;
  private final UserProjectService userProjectService;

  public AuthorizationSnapshotService(
          PermissionCache permissionCache,
          UserRoleService userRoleService,
          RolePermissionService rolePermissionService,
          PermissionDao permissionDao,
          UserMenuGrantService userMenuGrantService,
          UserProjectService userProjectService) {
    this.permissionCache = permissionCache;
    this.userRoleService = userRoleService;
    this.rolePermissionService = rolePermissionService;
    this.permissionDao = permissionDao;
    this.userMenuGrantService = userMenuGrantService;
    this.userProjectService = userProjectService;
  }

  public AuthorizationSnapshot get(Long userId) {
    if (userId == null) {
      return AuthorizationSnapshot.empty();
    }
    return permissionCache.getAuthorizationSnapshot(
            userId,
            () -> load(userId));
  }

  private AuthorizationSnapshot load(Long userId) {
    List<Long> roleIds = normalizeIds(
            userRoleService.getRoleIdListByUserId(userId));
    List<Long> permissionIds = roleIds.isEmpty()
            ? Collections.emptyList()
            : normalizeIds(
                    rolePermissionService
                            .getPermissionIdListByRoleIdList(roleIds));

    Set<String> permissionCodes =
            loadPermissionCodes(permissionIds);
    UserMenuGrantService.MenuGrant menuGrant =
            userMenuGrantService.resolve(roleIds, permissionIds);
    permissionCodes.addAll(menuGrant.getPermissionCodes());

    Set<Long> projectIds = permissionCodes.contains(
            SecurityPermissionCode.ROOT)
            ? Collections.emptySet()
            : loadProjectIds(userId);

    return new AuthorizationSnapshot(
            roleIds,
            permissionCodes,
            menuGrant.getMenuCodes(),
            projectIds);
  }

  private Set<Long> loadProjectIds(Long userId) {
    Set<Long> projectIds = new LinkedHashSet<>();
    List<Long> assignedProjectIds =
            userProjectService.getProjectIdListByUserIdList(
                    Collections.singletonList(userId));
    if (!CollectionUtils.isEmpty(assignedProjectIds)) {
      assignedProjectIds.stream()
              .filter(Objects::nonNull)
              .filter(projectId -> projectId > 0L)
              .forEach(projectIds::add);
    }
    return projectIds;
  }

  private Set<String> loadPermissionCodes(
          List<Long> permissionIds) {
    if (CollectionUtils.isEmpty(permissionIds)) {
      return new LinkedHashSet<>();
    }

    Set<Long> grantedIds = new HashSet<>(permissionIds);
    Set<String> permissionCodes = new LinkedHashSet<>();
    List<Permission> permissions =
            permissionDao.selectAllAndAscOrderByLevel();
    if (CollectionUtils.isEmpty(permissions)) {
      return permissionCodes;
    }

    for (Permission permission : permissions) {
      if (permission != null
              && grantedIds.contains(permission.getId())
              && Boolean.TRUE.equals(permission.getActive())
              && StringUtils.hasText(
              permission.getPermissionCode())) {
        permissionCodes.add(
                permission.getPermissionCode());
      }
    }
    return permissionCodes;
  }

  private List<Long> normalizeIds(List<Long> values) {
    if (CollectionUtils.isEmpty(values)) {
      return new ArrayList<>();
    }
    return values.stream()
            .filter(Objects::nonNull)
            .filter(value -> value > 0L)
            .distinct()
            .collect(Collectors.toList());
  }
}
