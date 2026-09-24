package io.yak.framework.security.context;

import java.util.List;
import java.util.Set;

/**
 * Yak Security 当前用户上下文的静态访问入口。
 *
 * <p>上下文与当前请求线程绑定，并在请求结束时自动清理。
 * 业务代码应优先注入 {@link CurrentUser}；非 Spring 管理的代码可使用此类。</p>
 */
public final class YakSecurityContext {

  private static final CurrentUser ANONYMOUS =
          new ImmutableCurrentUser(
                  null,
                  null,
                  null,
                  AuthorizationSnapshot.empty(),
                  false);

  private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

  private YakSecurityContext() {
    throw new IllegalStateException("Utility class");
  }

  public static Long getCurrentUserId() {
    return currentUser().getUserId();
  }

  public static String getCurrentUsername() {
    return currentUser().getUsername();
  }

  public static Long getCurrentProjectId() {
    return currentUser().getProjectId();
  }

  public static List<Long> getCurrentRoleIds() {
    return currentUser().getRoleIds();
  }

  public static Set<String> getCurrentPermissionCodes() {
    return currentUser().getPermissionCodes();
  }

  public static List<String> getCurrentMenuCodes() {
    return currentUser().getMenuCodes();
  }

  public static Set<Long> getCurrentProjectIds() {
    return currentUser().getProjectIds();
  }

  public static boolean hasPermission(String permissionCode) {
    return currentUser().hasPermission(permissionCode);
  }

  public static boolean canAccessProject(Long projectId) {
    return currentUser().canAccessProject(projectId);
  }

  public static boolean isAuthenticated() {
    return currentUser().isAuthenticated();
  }

  static CurrentUser currentUser() {
    CurrentUser currentUser = HOLDER.get();
    return currentUser == null ? ANONYMOUS : currentUser;
  }

  static void setCurrentUser(CurrentUser currentUser) {
    HOLDER.set(currentUser);
  }

  static void clear() {
    HOLDER.remove();
  }

  static final class ImmutableCurrentUser implements CurrentUser {
    private final Long userId;
    private final String username;
    private final Long projectId;
    private final AuthorizationSnapshot authorizationSnapshot;
    private final boolean authenticated;

    ImmutableCurrentUser(
            Long userId,
            String username,
            Long projectId,
            List<Long> roleIds,
            boolean authenticated) {
      this(
              userId,
              username,
              projectId,
              AuthorizationSnapshot.forRoleIds(roleIds),
              authenticated);
    }

    ImmutableCurrentUser(
            Long userId,
            String username,
            Long projectId,
            AuthorizationSnapshot authorizationSnapshot,
            boolean authenticated) {
      this.userId = userId;
      this.username = username;
      this.projectId = projectId;
      this.authorizationSnapshot = authorizationSnapshot == null
              ? AuthorizationSnapshot.empty()
              : authorizationSnapshot;
      this.authenticated = authenticated;
    }

    @Override
    public Long getUserId() {
      return userId;
    }

    @Override
    public String getUsername() {
      return username;
    }

    @Override
    public Long getProjectId() {
      return projectId;
    }

    @Override
    public List<Long> getRoleIds() {
      return authorizationSnapshot.getRoleIds();
    }

    @Override
    public Set<String> getPermissionCodes() {
      return authorizationSnapshot.getPermissionCodes();
    }

    @Override
    public List<String> getMenuCodes() {
      return authorizationSnapshot.getMenuCodes();
    }

    @Override
    public Set<Long> getProjectIds() {
      return authorizationSnapshot.getProjectIds();
    }

    @Override
    public boolean hasPermission(String permissionCode) {
      return authorizationSnapshot.hasPermission(permissionCode);
    }

    @Override
    public boolean canAccessProject(Long projectId) {
      return authorizationSnapshot.canAccessProject(projectId);
    }

    @Override
    public boolean isAuthenticated() {
      return authenticated;
    }
  }
}
