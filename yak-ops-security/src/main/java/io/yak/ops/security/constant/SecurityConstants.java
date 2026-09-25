package io.yak.ops.security.constant;

import io.yak.ops.common.constant.CommonConstants;

/**
 * Security 领域内跨模块共享的稳定常量。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public final class SecurityConstants {

    /** Security 配置根前缀。 */
    public static final String CONFIG_PREFIX = "yak.security";

    /** Security Bootstrap 配置前缀。 */
    public static final String BOOTSTRAP_CONFIG_PREFIX = CONFIG_PREFIX + ".bootstrap";

    /** Security API 根路径。 */
    public static final String API_PREFIX = "/yak-security" + CommonConstants.API_PREFIX;

    /** 登录与当前用户 API 根路径。 */
    public static final String ACCOUNT_API_PREFIX = API_PREFIX + "/account";

    /** 用户管理 API 根路径。 */
    public static final String USER_API_PREFIX = API_PREFIX + "/user";

    /** 登录 API 路径。 */
    public static final String LOGIN_API_PATH = ACCOUNT_API_PREFIX + "/login";

    private SecurityConstants() {}
}
