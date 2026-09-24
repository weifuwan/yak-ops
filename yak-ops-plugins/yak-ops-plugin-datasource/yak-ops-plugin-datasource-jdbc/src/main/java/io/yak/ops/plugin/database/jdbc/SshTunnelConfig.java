package io.yak.ops.plugin.database.jdbc;

/** JDBC 数据源可选的 SSH 隧道配置。 */
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

    public enum AuthType {
        PASSWORD,
        PRIVATE_KEY
    }

    public static SshTunnelConfig disabled() {
        return new SshTunnelConfig(false, null, 22, null, AuthType.PASSWORD, null, null, null, false, null);
    }
}
