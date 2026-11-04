package io.yak.ops.plugin.datasource.ssh;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceLoader;
import org.apache.sshd.common.util.security.SecurityUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Collection;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SshClientWrapper implements AutoCloseable {

    private SshClient sshClient;

    private final ClientSession clientSession;

    public SshClientWrapper(
                            String ip, Integer port, String userName, String password, String privateKey)
                                                                                                          throws IOException,
                                                                                                          GeneralSecurityException {
        checkNotNull(ip);
        checkNotNull(port);
        checkNotNull(userName);
        clientSession = createSession(ip, port, userName);
        if (StringUtils.isNotEmpty(password)) {
            clientSession.addPasswordIdentity(password);
        }
        if (StringUtils.isNotEmpty(privateKey)) {
            KeyPairResourceLoader loader = SecurityUtils.getKeyPairResourceParser();
            Collection<KeyPair> keyPairCollection = loader.loadKeyPairs(null, null, null, privateKey);
            for (KeyPair keyPair : keyPairCollection) {
                clientSession.addPublicKeyIdentity(keyPair);
            }
        }
    }

    public boolean isAuth() throws IOException {
        return clientSession.auth().verify(Duration.ofSeconds(10)).isSuccess();
    }

    private ClientSession createSession(String ip, Integer port, String userName) throws IOException {
        sshClient = SshClient.setUpDefaultClient();
        sshClient.start();
        return sshClient.connect(userName, ip, port).verify(Duration.ofSeconds(10)).getSession();
    }

    @Override
    public void close() throws Exception {
        try (
                ClientSession clientSession1 = clientSession;
                SshClient sshClient1 = sshClient) {
            // closed the resources
        }
    }
}
