package io.yak.ops.security.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.yak.ops.security.config.YakSecurityProperties;
import io.yak.ops.security.context.AuthorizationSnapshot;
import io.yak.ops.security.dao.UserRoleDao;
import io.yak.ops.security.service.PermissionCache;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * 基于 Caffeine 实现的权限缓存。
 *
 * <p>该缓存仅在当前应用实例内生效，主要用于缓存用户拥有的权限标识和授权快照，
 * 减少权限校验过程中对数据库的重复查询。</p>
 *
 * <p>缓存键由应用名称和用户 ID 组成，用于隔离不同应用下的用户权限数据。</p>
 *
 * @author weifuwan
 */
@Service
@ConditionalOnMissingBean(PermissionCache.class)
public class CaffeinePermissionCache implements PermissionCache {

  /**
   * 当前应用名称。
   */
  private final String applicationName;

  /**
   * 是否启用权限缓存。
   */
  private final boolean enabled;

  /**
   * 用户权限缓存。
   */
  private final Cache<Key, Set<String>> cache;

  /**
   * 用户统一授权快照缓存。
   */
  private final Cache<Key, AuthorizationSnapshot> authorizationCache;

  /**
   * 用户角色数据访问对象。
   */
  private final UserRoleDao userRoleDao;

  /**
   * 创建 Caffeine 权限缓存。
   *
   * @param properties 应用安全配置
   * @param userRoleDao 用户角色数据访问对象
   */
  public CaffeinePermissionCache(
          YakSecurityProperties properties,
          UserRoleDao userRoleDao) {

    YakSecurityProperties.PermissionCacheProperties settings =
            properties.getPermissionCache();
    long maximumSize =
            Math.max(1, settings.getMaximumSize());
    Duration ttl =
            Duration.ofMinutes(
                    Math.max(1, settings.getTtlMinutes()));

    this.applicationName = properties.getApplicationName();
    this.enabled = settings.isEnabled();
    this.userRoleDao = userRoleDao;
    this.cache = Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(ttl)
            .build();
    this.authorizationCache = Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(ttl)
            .build();
  }

  /**
   * 获取指定用户的权限集合。
   *
   * <p>启用缓存时，优先从缓存中读取；缓存不存在时，通过加载器查询权限并写入缓存。
   * 未启用缓存时，直接调用加载器获取权限。</p>
   *
   * @param userId 用户 ID
   * @param loader 权限加载器
   * @return 不可修改的权限集合
   */
  @Override
  public Set<String> get(
          Long userId,
          Supplier<Set<String>> loader) {

    if (userId == null) {
      return Collections.emptySet();
    }

    if (!enabled) {
      return immutable(loader.get());
    }

    Key key = new Key(applicationName, userId);

    return cache.get(
            key,
            ignored -> immutable(loader.get()));
  }

  /**
   * 获取指定用户的统一授权快照。
   *
   * @param userId 用户 ID
   * @param loader 授权快照加载器
   * @return 授权快照
   */
  @Override
  public AuthorizationSnapshot getAuthorizationSnapshot(
          Long userId,
          Supplier<AuthorizationSnapshot> loader) {

    if (userId == null) {
      return AuthorizationSnapshot.empty();
    }

    if (!enabled) {
      return safeSnapshot(loader.get());
    }

    Key key = new Key(applicationName, userId);

    return authorizationCache.get(
            key,
            ignored -> safeSnapshot(loader.get()));
  }

  /**
   * 清除指定用户的权限缓存。
   *
   * @param userId 用户 ID
   */
  @Override
  public void invalidateUser(Long userId) {
    if (userId == null) {
      return;
    }

    Key key = new Key(applicationName, userId);
    cache.invalidate(key);
    authorizationCache.invalidate(key);
  }

  /**
   * 清除指定角色关联用户的权限缓存。
   *
   * <p>角色权限发生变化时，查询拥有该角色的全部用户，
   * 并逐一清除对应的权限缓存。</p>
   *
   * @param roleId 角色 ID
   */
  @Override
  public void invalidateRole(Long roleId) {
    if (roleId == null) {
      return;
    }

    userRoleDao.selectUserIdListByRoleId(roleId)
            .forEach(this::invalidateUser);
  }

  /**
   * 清除当前应用实例中的全部权限缓存。
   */
  @Override
  public void invalidateAll() {
    cache.invalidateAll();
    authorizationCache.invalidateAll();
  }

  /**
   * 将权限集合转换为不可修改集合。
   *
   * <p>创建新的集合副本，避免外部修改原集合后影响缓存中的权限数据。</p>
   *
   * @param values 原始权限集合
   * @return 不可修改的权限集合
   */
  private static Set<String> immutable(Set<String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptySet();
    }

    return Collections.unmodifiableSet(
            new HashSet<>(values));
  }

  private static AuthorizationSnapshot safeSnapshot(
          AuthorizationSnapshot snapshot) {
    return snapshot == null
            ? AuthorizationSnapshot.empty()
            : snapshot;
  }

  /**
   * 权限缓存键。
   *
   * <p>由应用名称和用户 ID 共同组成，确保不同应用之间的用户权限缓存相互隔离。</p>
   *
   * @author weifuwan
   */
  private static final class Key {

    /**
     * 应用名称。
     */
    private final String applicationName;

    /**
     * 用户 ID。
     */
    private final Long userId;

    /**
     * 创建权限缓存键。
     *
     * @param applicationName 应用名称
     * @param userId 用户 ID
     */
    private Key(
            String applicationName,
            Long userId) {

      this.applicationName = applicationName;
      this.userId = userId;
    }

    /**
     * 判断两个缓存键是否相同。
     *
     * @param object 待比较对象
     * @return 是否相同
     */
    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }

      if (!(object instanceof Key)) {
        return false;
      }

      Key other = (Key) object;

      return Objects.equals(
              this.applicationName,
              other.applicationName)
              && Objects.equals(
              this.userId,
              other.userId);
    }

    /**
     * 计算缓存键的哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
      return Objects.hash(
              applicationName,
              userId);
    }

    /**
     * 返回缓存键的字符串表示。
     *
     * @return 缓存键信息
     */
    @Override
    public String toString() {
      return "Key{"
              + "applicationName='" + applicationName + '\''
              + ", userId=" + userId
              + '}';
    }
  }
}
