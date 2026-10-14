package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies explicit replication inputs without task-platform dependencies. */
class MySqlCdcFactoryCreationTest {
    private static final TableId TABLE = new TableId("app", null, "orders");
    private static final TableSchema SCHEMA = new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));

    @Test
    void consumesPreparedReplicationParametersAndCustomUrlPort() throws Exception {
        Configuration options = options();
        MySqlHybridCdcSource source = (MySqlHybridCdcSource) new MySqlCdcSourceFactory()
                .createSource(new TableSourceFactoryContext(options.toReadonlyConfig(), TABLE, SCHEMA));
        var field = MySqlHybridCdcSource.class.getDeclaredField("config");
        field.setAccessible(true);
        MySqlCdcSourceConfig config = (MySqlCdcSourceConfig) field.get(source);
        assertEquals(8123, config.serverId());
        assertEquals("replication_8123", config.topicPrefix());
        assertEquals(3307, config.port());
    }

    @Test
    void rejectsMissingReplicationParametersAndSshConnections() {
        Configuration options = options();
        options.removeConfig(MySqlCdcSourceOptions.SERVER_ID);
        assertThrows(IllegalArgumentException.class, () -> new MySqlCdcSourceFactory().createSource(new TableSourceFactoryContext(options.toReadonlyConfig(), TABLE, SCHEMA)));
        options.set(MySqlCdcSourceOptions.SERVER_ID, 8123).set(JdbcConnectionOptions.SSH_ENABLED, true);
        assertThrows(UnsupportedOperationException.class, () -> new MySqlCdcSourceFactory().createSource(new TableSourceFactoryContext(options.toReadonlyConfig(), TABLE, SCHEMA)));
    }

    @Test
    void registersAllTablesInOnePhysicalSourceAndRejectsConflictingSettings() throws Exception {
        var metrics = new io.yak.ops.connector.base.metrics.TableMetrics();
        var first = new TableSourceFactoryContext(options().toReadonlyConfig(), TABLE, SCHEMA, metrics);
        var second = new TableSourceFactoryContext(options().toReadonlyConfig(), new TableId("app", null, "items"), SCHEMA, metrics);
        var factory = new MySqlCdcSourceFactory();
        var source = (MySqlHybridCdcSource) factory.createSource(List.of(first, second));
        var field = MySqlHybridCdcSource.class.getDeclaredField("config");
        field.setAccessible(true);
        assertEquals(2, ((MySqlCdcSourceConfig) field.get(source)).schemas().size());
        var changed = new TableSourceFactoryContext(options().set(MySqlCdcSourceOptions.SERVER_ID, 1234).toReadonlyConfig(), second.table(), SCHEMA, metrics);
        assertThrows(IllegalArgumentException.class, () -> factory.createSource(List.of(first, changed)));
        assertThrows(IllegalArgumentException.class, () -> factory.createSource(List.of(first, first)));
        assertThrows(IllegalArgumentException.class, () -> factory.validateSchema(new TableSchema(List.of(new Column("id", LogicalTypes.STRING.copy(false))), List.of("id"))));
        assertThrows(IllegalArgumentException.class, () -> factory.validateSchema(new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT)), List.of("id"))));
    }

    private static Configuration options() {
        return new Configuration().set(JdbcConnectionOptions.URL, "jdbc:mysql://localhost:3307/app")
                .set(JdbcConnectionOptions.USERNAME, "user").set(JdbcConnectionOptions.PASSWORD, "secret")
                .set(MySqlCdcSourceOptions.SERVER_ID, 8123).set(MySqlCdcSourceOptions.TOPIC_PREFIX, "replication_8123");
    }
}
