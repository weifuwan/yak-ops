package io.yak.framework.security.extend.impl;

import io.yak.framework.security.extend.PermissionExtend;

/**
 * 默认权限扩展实现。
 *
 * <p>默认采用安全的拒绝策略。宿主应用需要额外权限能力时，
 * 应提供自己的 {@link PermissionExtend} 实现。</p>
 *
 * @author weifuwan
 */
public class DefaultPermissionExtend
        implements PermissionExtend {

  /**
   * 默认不授予任何额外权限。
   *
   * @param user 用户名
   * @param permission 权限标识
   * @return 固定返回 {@code false}
   */
  @Override
  public boolean hasPermission(
          String user,
          String permission) {

    return false;
  }
}