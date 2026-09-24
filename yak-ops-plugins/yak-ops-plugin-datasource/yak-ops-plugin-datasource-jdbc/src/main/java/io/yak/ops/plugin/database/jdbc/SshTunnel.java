package io.yak.ops.plugin.database.jdbc;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/** 单次 JDBC 连接使用的 SSH 本地端口转发，会随 JDBC Connection 一起释放。 */
final class SshTunnel implements AutoCloseable {

    private final Session session;
    private final int localPort;

    private SshTunnel(Session session, int localPort) {
        this.session = session;
        this.localPort = localPort;
    }

    static SshTunnel open(SshTunnelConfig config, String targetHost, int targetPort, int timeoutSeconds)
            throws JSchException {
        JSch jsch = new JSch();
        configureHostKeys(jsch, config);
        configureIdentity(jsch, config);

        Session session = jsch.getSession(config.username(), config.host(), config.port());
        if (config.authType() == SshTunnelConfig.AuthType.PASSWORD) {
            session.setPassword(config.password().getBytes(StandardCharsets.UTF_8));
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive");
        } else {
            session.setConfig("PreferredAuthentications", "publickey");
        }
        session.setConfig("StrictHostKeyChecking", config.strictHostKeyChecking() ? "yes" : "no");

        int timeoutMillis = Math.max(1, timeoutSeconds) * 1000;
        try {
            session.connect(timeoutMillis);
            int localPort = session.setPortForwardingL("127.0.0.1", 0, targetHost, targetPort);
            return new SshTunnel(session, localPort);
        } catch (JSchException exception) {
            session.disconnect();
            throw exception;
        }
    }

    int localPort() {
        return localPort;
    }

    @Override
    public void close() {
        if (session.isConnected()) {
            session.disconnect();
        }
    }

    private static void configureHostKeys(JSch jsch, SshTunnelConfig config) throws JSchException {
        if (!config.strictHostKeyChecking()) return;
        jsch.setKnownHosts(new ByteArrayInputStream(config.knownHosts().getBytes(StandardCharsets.UTF_8)));
    }

    private static void configureIdentity(JSch jsch, SshTunnelConfig config) throws JSchException {
        if (config.authType() != SshTunnelConfig.AuthType.PRIVATE_KEY) return;
        byte[] passphrase = config.passphrase() == null || config.passphrase().isEmpty()
                ? null
                : config.passphrase().getBytes(StandardCharsets.UTF_8);
        jsch.addIdentity("yak-ops-datasource", config.privateKey().getBytes(StandardCharsets.UTF_8), null, passphrase);
    }
}
