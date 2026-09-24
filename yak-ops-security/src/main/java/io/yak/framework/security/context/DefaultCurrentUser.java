package io.yak.framework.security.context;

import java.util.List;
import java.util.Set;

/** Spring 可注入的当前用户实现。 */
public final class DefaultCurrentUser implements CurrentUser {

  @Override
  public Long getUserId() {
    return YakSecurityContext.getCurrentUserId();
  }

  @Override
  public String getUsername() {
    return YakSecurityContext.getCurrentUsername();
  }

  @Override
  public Long getProjectId() {
    return YakSecurityContext.getCurrentProjectId();
  }

  @Override
  public List<Long> getRoleIds() {
    return YakSecurityContext.getCurrentRoleIds();
  }

  @Override
  public Set<String> getPermissionCodes() {
    return YakSecurityContext.getCurrentPermissionCodes();
  }

  @Override
  public List<String> getMenuCodes() {
    return YakSecurityContext.getCurrentMenuCodes();
  }

  @Override
  public Set<Long> getProjectIds() {
    return YakSecurityContext.getCurrentProjectIds();
  }

  @Override
  public boolean hasPermission(String permissionCode) {
    return YakSecurityContext.hasPermission(permissionCode);
  }

  @Override
  public boolean canAccessProject(Long projectId) {
    return YakSecurityContext.canAccessProject(projectId);
  }

  @Override
  public boolean isAuthenticated() {
    return YakSecurityContext.isAuthenticated();
  }
}
