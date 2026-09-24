package io.yak.framework.security.service;

import io.yak.framework.security.context.AuthorizationSnapshot;
import java.util.Set;
import java.util.function.Supplier;

/** Local cache of permission codes and authorization facts granted to a user. */
public interface PermissionCache {
  Set<String> get(Long userId, Supplier<Set<String>> loader);

  /**
   * 获取用户授权快照。
   *
   * <p>默认实现不缓存，保证已有自定义 PermissionCache 实现保持兼容；
   * 框架默认 Caffeine 实现会覆盖本方法并复用同一套失效语义。</p>
   */
  default AuthorizationSnapshot getAuthorizationSnapshot(
          Long userId,
          Supplier<AuthorizationSnapshot> loader) {
    if (userId == null) {
      return AuthorizationSnapshot.empty();
    }
    AuthorizationSnapshot snapshot = loader.get();
    return snapshot == null
            ? AuthorizationSnapshot.empty()
            : snapshot;
  }

  void invalidateUser(Long userId);

  void invalidateRole(Long roleId);

  void invalidateAll();
}
