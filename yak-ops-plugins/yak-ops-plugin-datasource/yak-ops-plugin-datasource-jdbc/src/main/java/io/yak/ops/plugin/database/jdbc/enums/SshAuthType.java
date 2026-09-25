package io.yak.ops.plugin.database.jdbc.enums;

/**
 * SSH 登录认证方式。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public enum SshAuthType {

    /** 用户名 + 密码认证。 */
    PASSWORD,

    /** 用户名 + 私钥认证。 */
    PRIVATE_KEY
}
