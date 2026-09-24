package io.yak.framework.security.permission;

import io.yak.framework.security.common.entity.Permission;
import io.yak.framework.security.dao.PermissionDao;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/** Reconciles declared permissions without deleting role grants. */
public class PermissionRegistrationService {
  private final PermissionDao permissionDao;

  public PermissionRegistrationService(PermissionDao permissionDao) {
    this.permissionDao = permissionDao;
  }

  @Transactional(transactionManager = "yakSecurityTransactionManager")
  public void synchronize(Collection<PermissionDefinition> definitions) {
    Map<String, Permission> desired = new LinkedHashMap<>();
    for (PermissionDefinition group : definitions) {
      putUnique(
          desired,
          permission(
              group.getCode(),
              group.getName(),
              null,
              null,
              false,
              1));
      for (PermissionDefinition.Item item : group.getPermissions()) {
        Permission permission = permission(
            item.getCode(),
            item.getName(),
            item.getDescription(),
            item.getMenuCode(),
            true,
            2);
        permission.setParentCode(group.getCode());
        putUnique(desired, permission);
      }
    }
    permissionDao.synchronizeDeclared(
        new java.util.ArrayList<>(desired.values()));
  }

  private static Permission permission(
      String code,
      String name,
      String description,
      String menuCode,
      boolean leaf,
      int level) {
    Permission permission = new Permission();
    permission.setPermissionCode(code);
    permission.setPermissionName(name);
    permission.setDescription(description);
    permission.setMenuCode(menuCode);
    permission.setLeaf(leaf);
    permission.setLevel(level);
    permission.setActive(true);
    permission.setDeclared(true);
    return permission;
  }

  private static void putUnique(
      Map<String, Permission> permissions,
      Permission permission) {
    Permission previous = permissions.putIfAbsent(
        permission.getPermissionCode(),
        permission);
    if (previous != null
        && (!previous.getPermissionName().equals(
            permission.getPermissionName())
            || previous.getLeaf() != permission.getLeaf())) {
      throw new IllegalStateException(
          "Conflicting permission declaration: "
              + permission.getPermissionCode());
    }
  }
}
