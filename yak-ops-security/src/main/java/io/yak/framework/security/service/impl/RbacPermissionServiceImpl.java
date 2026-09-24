package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.context.AuthorizationSnapshot;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.framework.security.extend.PermissionExtend;
import io.yak.framework.security.service.RbacPermissionService;
import io.yak.framework.security.service.UserService;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Default database-backed RBAC permission checker. */
@Service
public class RbacPermissionServiceImpl implements RbacPermissionService {

  private final UserService userService;
  private final PermissionExtend permissionExtend;
  private final AuthorizationSnapshotService authorizationSnapshotService;

  public RbacPermissionServiceImpl(
          UserService userService,
          PermissionExtend permissionExtend,
          AuthorizationSnapshotService authorizationSnapshotService) {
    this.userService = userService;
    this.permissionExtend = permissionExtend;
    this.authorizationSnapshotService = authorizationSnapshotService;
  }

  @Override
  public boolean hasPermission(
          String userName,
          String permissionCode) {

    if (!StringUtils.hasText(userName)
            || !StringUtils.hasText(permissionCode)) {
      return false;
    }

    Long currentUserId = currentRequestUserId(userName);
    if (currentUserId != null) {
      if (YakSecurityContext.hasPermission(permissionCode)) {
        return true;
      }
      return permissionExtend.hasPermission(
              userName,
              permissionCode);
    }

    User user = userService.getUserByUsername(userName);
    if (user != null && user.getId() != null) {
      AuthorizationSnapshot snapshot =
              authorizationSnapshotService.get(user.getId());
      if (snapshot.hasPermission(permissionCode)) {
        return true;
      }
    }

    return permissionExtend.hasPermission(
            userName,
            permissionCode);
  }

  private Long currentRequestUserId(String userName) {
    Long currentUserId = YakSecurityContext.getCurrentUserId();
    if (currentUserId == null
            || !Objects.equals(
            userName,
            YakSecurityContext.getCurrentUsername())) {
      return null;
    }
    return currentUserId;
  }
}
