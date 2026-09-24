package io.yak.ops.security.extend;

/**
 * 权限校验扩展点。
 *
 * <p>宿主应用可以在 Yak 内置权限模型之外增加额外的权限判断。</p>
 *
 * @author weifuwan
 */
@FunctionalInterface
public interface PermissionExtend {

  /**
   * 判断用户是否拥有指定权限。
   *
   * @param user 用户名
   * @param permission 权限标识
   * @return 拥有权限返回 {@code true}
   */
  boolean hasPermission(
          String user,
          String permission);
}