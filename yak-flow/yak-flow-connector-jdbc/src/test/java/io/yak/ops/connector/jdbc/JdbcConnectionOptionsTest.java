package io.yak.ops.connector.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.connector.base.factory.SourceFactoryContext;
import io.yak.ops.connector.jdbc.source.MySqlDataSourceFactory;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies optional SSH defaults without weakening required credentials or value validation. */
class JdbcConnectionOptionsTest {

    @Test
    void createsSourceWithTheDefaultSshPortWithoutMakingItExplicit() {
        ReadonlyConfig config = sshOptions().toReadonlyConfig();
        JdbcConnectionOptions connection = new JdbcConnectionOptions(config);
        assertEquals(22, connection.configuration().get(JdbcConnectionOptions.SSH_PORT));
        assertFalse(config.contains(JdbcConnectionOptions.SSH_PORT));
        TableSchema schema = new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        TableSourceFactoryContext context =
                new TableSourceFactoryContext(config, new TableId("app", null, "items"), schema);
        assertEquals(Boundedness.BOUNDED, new MySqlDataSourceFactory()
                .createDataSource(new SourceFactoryContext(List.of(context), new Configuration(), getClass().getClassLoader()))
                .getEventSourceProvider().getSource().getBoundedness());
    }

    @Test
    void acceptsExplicitDefaultAndNonDefaultSshPorts() {
        for (int port : List.of(22, 2222, 65535)) {
            JdbcConnectionOptions connection = new JdbcConnectionOptions(
                    sshOptions().set(JdbcConnectionOptions.SSH_PORT, port).toReadonlyConfig());
            assertEquals(port, connection.configuration().get(JdbcConnectionOptions.SSH_PORT));
        }
    }

    @Test
    void rejectsOutOfRangeSshPorts() {
        for (int port : List.of(-1, 0, 65536)) {
            ReadonlyConfig options =
                    sshOptions().set(JdbcConnectionOptions.SSH_PORT, port).toReadonlyConfig();
            assertThrows(IllegalArgumentException.class, () -> new JdbcConnectionOptions(options));
        }
    }

    @Test
    void defaultPortDoesNotMakeSshCredentialsOptional() {
        Configuration options = sshOptions();
        options.removeConfig(JdbcConnectionOptions.SSH_PASSWORD);
        assertThrows(IllegalArgumentException.class, () -> new JdbcConnectionOptions(options.toReadonlyConfig()));
    }

    @Test
    void appliesTheSameDefaultToNestedConfiguration() {
        ReadonlyConfig options = ReadonlyConfig.fromMap(Map.of(
                "connection",
                Map.of(
                        "url",
                        "jdbc:mysql://database.internal:3306/app",
                        "host",
                        "database.internal",
                        "port",
                        3306,
                        "ssh",
                        Map.of(
                                "enabled", true,
                                "host", "bastion",
                                "username", "test-user",
                                "password", "test-password",
                                "jdbc-url", "jdbc:mysql://127.0.0.1:{port}/app"))));
        JdbcConnectionOptions connection = new JdbcConnectionOptions(options);
        assertEquals(22, connection.configuration().get(JdbcConnectionOptions.SSH_PORT));
        assertFalse(options.contains(JdbcConnectionOptions.SSH_PORT));
    }

    private static Configuration sshOptions() {
        return new Configuration()
                .set(JdbcConnectionOptions.URL, "jdbc:mysql://database.internal:3306/app")
                .set(JdbcConnectionOptions.HOST, "database.internal")
                .set(JdbcConnectionOptions.PORT, 3306)
                .set(JdbcConnectionOptions.SSH_ENABLED, true)
                .set(JdbcConnectionOptions.SSH_HOST, "bastion")
                .set(JdbcConnectionOptions.SSH_USERNAME, "test-user")
                .set(JdbcConnectionOptions.SSH_PASSWORD, "test-password")
                .set(JdbcConnectionOptions.SSH_JDBC_URL, "jdbc:mysql://127.0.0.1:{port}/app");
    }
}
