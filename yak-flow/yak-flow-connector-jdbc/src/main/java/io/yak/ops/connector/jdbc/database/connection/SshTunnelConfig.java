package io.yak.ops.connector.jdbc.database.connection;

import java.util.Objects;

/**
 * Connection-specific SSH tunnel settings for a JDBC datasource provider.
 *
 * <p>Credentials remain accessible to trusted connection code but are excluded from diagnostics.
 */
public final class SshTunnelConfig {

    private final boolean enabled;
    private final String host;
    private final int port;
    private final String username;
    private final SshAuthType authType;
    private final String password;
    private final String privateKey;
    private final String passphrase;
    private final boolean strictHostKeyChecking;
    private final String knownHosts;

    /**
     * Creates the requested tunnel settings.
     *
     * @param enabled whether SSH tunneling is enabled
     * @param host SSH server host
     * @param port SSH server port
     * @param username SSH login username
     * @param authType authentication method
     * @param password credential for PASSWORD authentication
     * @param privateKey private-key contents for PRIVATE_KEY authentication
     * @param passphrase private-key passphrase
     * @param strictHostKeyChecking whether server host keys are strictly verified
     * @param knownHosts known_hosts contents used for strict verification
     */
    public SshTunnelConfig(
            boolean enabled,
            String host,
            int port,
            String username,
            SshAuthType authType,
            String password,
            String privateKey,
            String passphrase,
            boolean strictHostKeyChecking,
            String knownHosts) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.username = username;
        this.authType = authType;
        this.password = password;
        this.privateKey = privateKey;
        this.passphrase = passphrase;
        this.strictHostKeyChecking = strictHostKeyChecking;
        this.knownHosts = knownHosts;
    }

    /** Returns the default configuration with SSH tunneling disabled. */
    public static SshTunnelConfig disabled() {
        return new SshTunnelConfig(false, null, 22, null, SshAuthType.PASSWORD, null, null, null, false, null);
    }

    public boolean enabled() {
        return enabled;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String username() {
        return username;
    }

    public SshAuthType authType() {
        return authType;
    }

    public String password() {
        return password;
    }

    public String privateKey() {
        return privateKey;
    }

    public String passphrase() {
        return passphrase;
    }

    public boolean strictHostKeyChecking() {
        return strictHostKeyChecking;
    }

    public String knownHosts() {
        return knownHosts;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SshTunnelConfig that)) {
            return false;
        }
        return enabled == that.enabled
                && port == that.port
                && strictHostKeyChecking == that.strictHostKeyChecking
                && Objects.equals(host, that.host)
                && Objects.equals(username, that.username)
                && authType == that.authType
                && Objects.equals(password, that.password)
                && Objects.equals(privateKey, that.privateKey)
                && Objects.equals(passphrase, that.passphrase)
                && Objects.equals(knownHosts, that.knownHosts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                enabled,
                host,
                port,
                username,
                authType,
                password,
                privateKey,
                passphrase,
                strictHostKeyChecking,
                knownHosts);
    }

    @Override
    public String toString() {
        return "SshTunnelConfig{credentials=redacted}";
    }
}
