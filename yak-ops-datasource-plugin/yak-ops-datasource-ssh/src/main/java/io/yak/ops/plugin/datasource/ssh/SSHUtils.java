package io.yak.ops.plugin.datasource.ssh;

import io.yak.ops.plugin.datasource.ssh.param.SSHConnectionParam;

import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceLoader;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.apache.sshd.common.util.security.SecurityUtils;

import java.security.KeyPair;
import java.time.Duration;
import java.util.Collection;

public class SSHUtils {

    private SSHUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static ClientSession getSession(SshClient client, SSHConnectionParam connectionParam) throws Exception {
        ClientSession session;
        session = client.connect(connectionParam.getUser(), connectionParam.getHost(), connectionParam.getPort())
                .verify(5000).getSession();
        // add password identity
        String password = connectionParam.getPassword();
        if (StringUtils.isNotEmpty(password)) {
            session.addPasswordIdentity(password);
        }

        // add private key identity
        String privateKey = connectionParam.getPrivateKey();
        if (StringUtils.isNotEmpty(privateKey)) {
            try {
                KeyPairResourceLoader loader = SecurityUtils.getKeyPairResourceParser();
                Collection<KeyPair> keyPairCollection = loader.loadKeyPairs(null, null, null, privateKey);
                for (KeyPair keyPair : keyPairCollection) {
                    session.addPublicKeyIdentity(keyPair);
                }
            } catch (Exception e) {
                throw new Exception("Failed to add public key identity", e);
            }
        }
        session.setSessionHeartbeat(SessionHeartbeatController.HeartbeatType.IGNORE, Duration.ofSeconds(3));
        return session;
    }
}
