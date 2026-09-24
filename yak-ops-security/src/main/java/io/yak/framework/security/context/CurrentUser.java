package io.yak.framework.security.context;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 当前请求中已验证的用户上下文。
 *
 * <p>业务 Service 可直接注入本接口，无需在方法间反复传递 operator。</p>
 */
public interface CurrentUser {

  Long getUserId();

  String getUsername();

  Long getProjectId();

  List<Long> getRoleIds();

  default Set<String> getPermissionCodes() {
    return Collections.emptySet();
  }

  default List<String> getMenuCodes() {
    return Collections.emptyList();
  }

  default Set<Long> getProjectIds() {
    return Collections.emptySet();
  }

  default boolean hasPermission(String permissionCode) {
    return new AuthorizationSnapshot(
            getRoleIds(),
            getPermissionCodes(),
            getMenuCodes(),
            getProjectIds())
            .hasPermission(permissionCode);
  }

  default boolean canAccessProject(Long projectId) {
    return new AuthorizationSnapshot(
            getRoleIds(),
            getPermissionCodes(),
            getMenuCodes(),
            getProjectIds())
            .canAccessProject(projectId);
  }

  boolean isAuthenticated();
}
