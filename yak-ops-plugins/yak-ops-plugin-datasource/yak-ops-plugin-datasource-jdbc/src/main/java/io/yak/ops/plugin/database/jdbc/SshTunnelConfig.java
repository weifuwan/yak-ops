package io.yak.ops.plugin.database.jdbc;

/**
 * JDBC Provider 可选的 SSH 隧道连接配置。
 *
 * @param enabled 是否启用 SSH 隧道
 * @param host SSH Server 地址
 * @param port SSH Server 端口
 * @param username SSH 登录用户名
 * @param authType SSH 认证方式
 * @param password PASSWORD 模式使用的密码
 * @param privateKey PRIVATE_KEY 模式使用的私钥内容
 * @param passphrase 私钥 passphrase
 * @param strictHostKeyChecking 是否启用严格主机校验
 * @param knownHosts 严格主机校验使用的 known_hosts 内容
 * @author weifuwan
 * @since 2026-09-24
 */
public record SshTunnelConfig(
        boolean enabled,
        String host,
        int port,
        String username,
        AuthType authType,
        String password,
        String privateKey,
        String passphrase,
        boolean strictHostKeyChecking,
        String knownHosts) {

    /**
     * SSH 登录认证方式。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    public enum AuthType {

        /** 用户名 + 密码认证。 */
        PASSWORD,

        /** 用户名 + 私钥认证。 */
        PRIVATE_KEY
    }

    /** @return 关闭 SSH 隧道的默认配置 */
    public static SshTunnelConfig disabled() {
        return new SshTunnelConfig(false, null, 22, null, AuthType.PASSWORD, null, null, null, false, null);
    }
}
