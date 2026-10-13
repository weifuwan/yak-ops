package io.yak.ops.plugin.database.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
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
        assertEquals("MYSQL_5", options.get(JdbcConnectionOptions.DRIVER_ID));
        assertEquals("private-password", options.get(JdbcConnectionOptions.PASSWORD));
        assertEquals("7000", options.get(JdbcConnectionOptions.PROPERTIES).get("connectTimeout"));
        assertEquals(11, options.get(JdbcConnectionOptions.TIMEOUT));
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
            assertTrue(options.get(JdbcConnectionOptions.SSH_ENABLED));
            assertTrue(options.get(JdbcConnectionOptions.SSH_JDBC_URL).contains("127.0.0.1:{port}"));
            assertEquals("database.internal", options.get(JdbcConnectionOptions.HOST));
            assertEquals("ssh-secret", options.get(JdbcConnectionOptions.SSH_PASSWORD));
            new JdbcConnectionOptions(options);
            for (String secret : List.of("db-secret", "ssh-secret", "phrase-secret")) { assertFalse(options.toString().contains(secret)); }
        }
    }
}
