package io.yak.ops.security.authentication;

/**
 * 定义 Security 登录态的建立、读取和销毁边界。
 *
 * <p>该 Contract 只管理认证会话生命周期，不负责账号密码校验、RBAC 或项目权限判断。</p>
 *
 * @author weifuwan
 * @since 2026-09-24
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
