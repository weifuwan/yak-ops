package io.yak.framework.security.context;

import io.yak.framework.security.common.constant.SecurityPermissionCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 当前用户授权事实的不可变快照。
 *
 * <p>角色、权限、菜单和项目范围在同一个缓存周期内一起加载，
 * 请求链路只读取快照，避免各授权组件重复访问安全库。</p>
 */
public final class AuthorizationSnapshot {

  private static final AuthorizationSnapshot EMPTY =
          new AuthorizationSnapshot(
                  Collections.emptyList(),
                  Collections.emptySet(),
                  Collections.emptyList(),
                  Collections.emptySet());

  private final List<Long> roleIds;
  private final Set<String> permissionCodes;
  private final List<String> menuCodes;
  private final Set<Long> projectIds;

  public AuthorizationSnapshot(
          Collection<Long> roleIds,
          Collection<String> permissionCodes,
          Collection<String> menuCodes,
          Collection<Long> projectIds) {
    this.roleIds = immutableIdList(roleIds);
    this.permissionCodes = immutableStringSet(permissionCodes);
    this.menuCodes = immutableStringList(menuCodes);
    this.projectIds = immutableIdSet(projectIds);
  }

  public static AuthorizationSnapshot empty() {
    return EMPTY;
  }

  public static AuthorizationSnapshot forRoleIds(
          Collection<Long> roleIds) {
    return new AuthorizationSnapshot(
            roleIds,
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptySet());
  }

  public List<Long> getRoleIds() {
    return roleIds;
  }

  public Set<String> getPermissionCodes() {
    return permissionCodes;
  }

  public List<String> getMenuCodes() {
    return menuCodes;
  }

  public Set<Long> getProjectIds() {
    return projectIds;
  }

  public boolean hasPermission(String permissionCode) {
    if (permissionCode == null || permissionCode.isBlank()) {
      return false;
    }
    return permissionCodes.contains(SecurityPermissionCode.ROOT)
            || permissionCodes.contains(permissionCode);
  }

  public boolean canAccessProject(Long projectId) {
    return projectId != null
            && (permissionCodes.contains(SecurityPermissionCode.ROOT)
            || projectIds.contains(projectId));
  }

  private static List<Long> immutableIdList(
          Collection<Long> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    LinkedHashSet<Long> normalized = new LinkedHashSet<>();
    values.stream()
            .filter(Objects::nonNull)
            .filter(value -> value > 0L)
            .forEach(normalized::add);
    return normalized.isEmpty()
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(normalized));
  }

  private static Set<Long> immutableIdSet(
          Collection<Long> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptySet();
    }
    LinkedHashSet<Long> normalized = new LinkedHashSet<>();
    values.stream()
            .filter(Objects::nonNull)
            .filter(value -> value > 0L)
            .forEach(normalized::add);
    return normalized.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(normalized);
  }

  private static List<String> immutableStringList(
          Collection<String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    values.stream()
            .filter(Objects::nonNull)
            .filter(value -> !value.isBlank())
            .forEach(normalized::add);
    return normalized.isEmpty()
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(normalized));
  }

  private static Set<String> immutableStringSet(
          Collection<String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptySet();
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    values.stream()
            .filter(Objects::nonNull)
            .filter(value -> !value.isBlank())
            .forEach(normalized::add);
    return normalized.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(normalized);
  }
}
