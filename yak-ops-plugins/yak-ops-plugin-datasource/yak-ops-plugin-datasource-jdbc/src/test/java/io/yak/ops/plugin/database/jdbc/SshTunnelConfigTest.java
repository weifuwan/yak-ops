package io.yak.ops.plugin.database.jdbc;

import io.yak.ops.connector.jdbc.database.connection.SshTunnelConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.database.connection.SshAuthType;
import java.util.List;
import org.junit.jupiter.api.Test;

class SshTunnelConfigTest {

    @Test
    void retainsConfigurationValuesAndRecordEquality() {
        SshTunnelConfig config = tunnelConfig();
        assertEquals("secret-password-123", config.password());
        assertEquals("secret-private-key-456", config.privateKey());
        assertEquals("secret-passphrase-789", config.passphrase());
        assertEquals(SshAuthType.PRIVATE_KEY, config.authType());
        assertEquals(config, tunnelConfig());
        assertEquals(config.hashCode(), tunnelConfig().hashCode());
        assertFalse(SshTunnelConfig.disabled().enabled());
    }

    @Test
    void diagnosticStringDoesNotExposeTunnelCredentials() {
        String diagnostic = tunnelConfig().toString();
        for (String sensitive : List.of(
                "secret-password-123",
                "secret-private-key-456",
                "secret-passphrase-789",
                "ssh.internal",
                "trusted-user",
                "known-hosts-content")) {
            assertFalse(diagnostic.contains(sensitive));
        }
        assertTrue(diagnostic.contains("redacted"));
    }

    private static SshTunnelConfig tunnelConfig() {
        return new SshTunnelConfig(
                true,
                "ssh.internal",
                2222,
                "trusted-user",
                SshAuthType.PRIVATE_KEY,
                "secret-password-123",
                "secret-private-key-456",
                "secret-passphrase-789",
                true,
                "known-hosts-content");
    }
}
