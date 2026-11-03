package io.yak.ops.plugin.database.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.plugin.database.jdbc.mysql.MySqlDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.oracle.OracleDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.postgresql.PostgreSqlDataSourcePlugin;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the one-way translation from saved datasource settings to execution inputs. */
class JdbcConnectionPreparationTest {
    @Test
    void preservesDriverSelectionCredentialsAndPropertiesWithoutOpeningConnections() {
        var plugin = new MySqlDataSourcePlugin();
        var connection = plugin.parseConnection(ConfigParser.parseJson("""
                {"host":"localhost","database":"app","username":"reader","password":"private-password",
                 "driverId":"MYSQL_5","properties":{"connectTimeout":7000}}
                """));
        var options = plugin.connectionOptions(connection, 11);
        assertEquals("MYSQL_5", options.getSourceMap().get("connection.driver-id"));
        assertEquals("private-password", options.getSourceMap().get("connection.password"));
        assertEquals("7000", ((java.util.Map<?, ?>) options.getSourceMap().get("connection.properties")).get("connectTimeout"));
        assertEquals(11, options.getSourceMap().get("connection.timeout-seconds"));
        assertFalse(options.toString().contains("private-password"));
        assertFalse(options.toString().contains("jdbc:mysql:"));
        assertTrue(connection.normalizedJson().contains("driverId"));
        assertFalse(connection.normalizedJson().contains("connection."));
    }

    @Test
    void preparesVendorBuiltSshUrlsAndKeepsSecretsOutOfDiagnostics() {
        for (AbstractJdbcDataSourcePlugin plugin : List.of(new MySqlDataSourcePlugin(), new OracleDataSourcePlugin(), new PostgreSqlDataSourcePlugin())) {
            var connection = plugin.parseConnection(ConfigParser.parseJson("""
                    {"host":"database.internal","database":"app","username":"reader","password":"db-secret",
                     "sshTunnel":{"enabled":true,"host":"bastion","username":"ssh-user","password":"ssh-secret",
                                  "passphrase":"phrase-secret","strictHostKeyChecking":false}}
                    """));
            var options = plugin.connectionOptions(connection, 9);
            assertEquals(Boolean.TRUE, options.getSourceMap().get("connection.ssh.enabled"));
            assertTrue(((String) options.getSourceMap().get("connection.ssh.jdbc-url")).contains("127.0.0.1:{port}"));
            assertEquals("database.internal", options.getSourceMap().get("connection.host"));
            assertEquals("ssh-secret", options.getSourceMap().get("connection.ssh.password"));
            for (String secret : List.of("db-secret", "ssh-secret", "phrase-secret")) { assertFalse(options.toString().contains(secret)); }
        }
    }
}
