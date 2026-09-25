package io.yak.ops.security.authentication;

/**
 * 登录态管理边界。
 *
 * <p>只负责建立、读取和销毁登录态，不负责账号密码校验、RBAC 或项目权限。</p>
 */
public interface AuthenticationManager {

    default void login(String userId) {
        throw new UnsupportedOperationException("login(userId, userName) is required");
    }

    void login(String userId, String userName);

    void logout();

    default void logoutUser(String userId) {
        // Authentication implementations may override account-level logout.
    }

    boolean isLogin();

    String getLoginUserId();

    String getLoginUsername();
}
