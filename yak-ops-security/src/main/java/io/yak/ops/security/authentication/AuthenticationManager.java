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

    /**
     * 兼容旧调用形式；当前实现要求同时提供用户名。
     *
     * @param userId 用户 ID
     */
    default void login(String userId) {
        throw new UnsupportedOperationException("login(userId, userName) is required");
    }

    /**
     * 为当前请求建立登录态。
     *
     * @param userId 用户 ID
     * @param userName 用户名
     */
    void login(String userId, String userName);

    /** 销毁当前请求对应的登录态。 */
    void logout();

    /**
     * 销毁指定用户的全部活动登录态；不支持账号级下线的实现可保持默认行为。
     *
     * @param userId 用户 ID
     */
    default void logoutUser(String userId) {
        // 账号级下线是可选能力，支持多会话索引的实现按需覆盖。
    }

    /** @return 当前请求是否存在有效登录态 */
    boolean isLogin();

    /** @return 当前登录用户 ID，未登录时返回 null */
    String getLoginUserId();

    /** @return 当前登录用户名，未登录时返回 null */
    String getLoginUsername();
}
