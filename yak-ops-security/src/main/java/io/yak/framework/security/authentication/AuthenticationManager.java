package io.yak.framework.security.authentication;

/**
 * 登录态管理边界。
 *
 * <p>该接口只描述登录态，不负责账号密码校验、用户查询、RBAC、项目权限等业务安全逻辑。
 * Yak Security 上层代码只依赖该边界，不直接依赖具体 Token 框架。</p>
 */
public interface AuthenticationManager {

  void login(Long userId);

  default void login(Long userId, String userName) {
    login(userId);
  }

  void logout();

  default void logoutUser(Long userId) {
    // Custom authentication implementations may override account-level logout.
  }

  boolean isLogin();

  Long getLoginUserId();

  default String getLoginUsername() {
    return null;
  }
}
