package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies explicit replication inputs without task-platform dependencies. */
class MySqlCdcFactoryCreationTest {
    private static final TableId TABLE = new TableId("app", null, "orders");
    private static final TableSchema SCHEMA = new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));

    @Test
    void consumesPreparedReplicationParametersAndCustomUrlPort() {
        Configuration options = options();
        // The latest-offset source exposes the same configuration used by the initial snapshot path.
        options.set(MySqlCdcSourceOptions.STARTUP_MODE, "latest-offset");
        var source = assertInstanceOf(
                MySqlSource.class,
                new MySqlCdcSourceFactory()
                        .createSource(new TableSourceFactoryContext(options.toReadonlyConfig(), TABLE, SCHEMA)));
        MySqlCdcSourceConfig config = source.config();
        assertEquals(8123, config.serverId());
        assertEquals("replication_8123", config.topicPrefix());
        assertEquals(3307, config.port());
    }

    @Test
    void propagatesUrlAndAdvancedDriverSettingsToSnapshotAndDebezium() {
        Configuration options = options()
                .set(JdbcConnectionOptions.URL,
                        "jdbc:mysql://localhost:3307/app?connectionTimeZone=Asia%2FShanghai&sslMode=REQUIRED&socketTimeout=12000")
                .set(JdbcConnectionOptions.PROPERTIES, Map.of(
                        "connectionTimeZone", "Europe/Berlin",
                        "sslMode", "DISABLED",
                        "allowPublicKeyRetrieval", "true"));
        options.set(MySqlCdcSourceOptions.STARTUP_MODE, "latest-offset");
        var source = assertInstanceOf(
                MySqlSource.class,
                new MySqlCdcSourceFactory()
                        .createSource(new TableSourceFactoryContext(options.toReadonlyConfig(), TABLE, SCHEMA)));
        MySqlCdcSourceConfig config = source.config();
        assertEquals("Asia/Shanghai", config.connectionProperties().get("connectionTimeZone"));
        assertEquals("REQUIRED", config.connectionProperties().get("sslMode"));
        assertEquals("12000", config.connectionProperties().get("socketTimeout"));
        assertEquals("true", config.connectionProperties().get("allowPublicKeyRetrieval"));
        assertTrue(!config.connectionProperties().containsKey("serverTimezone"));
        var debezium = config.debeziumProperties("test-session", Path.of("schema-history.dat"));
        assertEquals("Asia/Shanghai", debezium.getProperty("driver.connectionTimeZone"));
        assertEquals("REQUIRED", debezium.getProperty("driver.sslMode"));
        assertEquals("12000", debezium.getProperty("driver.socketTimeout"));
        assertEquals("true", debezium.getProperty("driver.allowPublicKeyRetrieval"));
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
    void registersAllTablesInOnePhysicalSourceAndRejectsConflictingSettings() {
        var metrics = new io.yak.ops.connector.base.metrics.TableMetrics();
        var first = new TableSourceFactoryContext(options().toReadonlyConfig(), TABLE, SCHEMA, metrics);
        var second = new TableSourceFactoryContext(options().toReadonlyConfig(), new TableId("app", null, "items"), SCHEMA, metrics);
        var factory = new MySqlCdcSourceFactory();
        assertInstanceOf(MySqlSource.class, factory.createSource(List.of(first, second)));
        var latest = options().set(MySqlCdcSourceOptions.STARTUP_MODE, "latest-offset").toReadonlyConfig();
        var binlog = assertInstanceOf(
                MySqlSource.class,
                factory.createSource(List.of(
                        new TableSourceFactoryContext(latest, TABLE, SCHEMA, metrics),
                        new TableSourceFactoryContext(latest, second.table(), SCHEMA, metrics))));
        assertEquals(StartupMode.LATEST_OFFSET, binlog.startupMode());
        assertEquals(2, binlog.config().schemas().size());
        assertTrue(binlog.config().schemas().containsKey(second.table()));
        var changed = new TableSourceFactoryContext(options().set(MySqlCdcSourceOptions.SERVER_ID, 1234).toReadonlyConfig(), second.table(), SCHEMA, metrics);
        assertThrows(IllegalArgumentException.class, () -> factory.createSource(List.of(first, changed)));
        assertThrows(IllegalArgumentException.class, () -> factory.createSource(List.of(first, first)));
        assertThrows(IllegalArgumentException.class, () -> factory.validateSchema(new TableSchema(List.of(new Column("id", LogicalTypes.STRING.copy(false))), List.of("id"))));
        assertThrows(IllegalArgumentException.class, () -> factory.validateSchema(new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT)), List.of("id"))));
    }


    @Test
    void startupOptionsSelectHybridByDefaultAndBinlogOnlyFromLatest() {
        var factory = new MySqlCdcSourceFactory();
        var initial = factory.createSource(new TableSourceFactoryContext(options().toReadonlyConfig(), TABLE, SCHEMA));
        assertEquals(StartupMode.INITIAL, assertInstanceOf(MySqlSource.class, initial).startupMode());

        var explicitInitial = options().set(MySqlCdcSourceOptions.STARTUP_MODE, "initial");
        assertEquals(
                StartupMode.INITIAL,
                assertInstanceOf(
                                MySqlSource.class,
                                factory.createSource(new TableSourceFactoryContext(
                                        explicitInitial.toReadonlyConfig(), TABLE, SCHEMA)))
                        .startupMode());

        // A nonnumeric primary key can identify Binlog changes without serving as a snapshot Chunk Key.
        var textKey = new TableSchema(
                List.of(new Column("code", LogicalTypes.varchar(64).copy(false))), List.of("code"));
        var latest = options().set(MySqlCdcSourceOptions.STARTUP_MODE, "latest-offset");
        var binlog = factory.createSource(new TableSourceFactoryContext(latest.toReadonlyConfig(), TABLE, textKey));
        assertEquals(StartupMode.LATEST_OFFSET, assertInstanceOf(MySqlSource.class, binlog).startupMode());
    }

    @Test
    void latestStartupRejectsUnsupportedModesAndSnapshotOnlySettings() {
        var factory = new MySqlCdcSourceFactory();
        var invalid = options().set(MySqlCdcSourceOptions.STARTUP_MODE, "earliest-offset");
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.createSource(new TableSourceFactoryContext(invalid.toReadonlyConfig(), TABLE, SCHEMA)));

        var latest = options().set(MySqlCdcSourceOptions.STARTUP_MODE, "latest-offset");
        var withChunk = latest.set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "id");
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.createSource(new TableSourceFactoryContext(withChunk.toReadonlyConfig(), TABLE, SCHEMA)));

        var keyless = new TableSchema(SCHEMA.columns(), List.of());
        var latestWithoutChunk = options().set(MySqlCdcSourceOptions.STARTUP_MODE, "latest-offset");
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.createSource(
                        new TableSourceFactoryContext(latestWithoutChunk.toReadonlyConfig(), TABLE, keyless)));
    }

    @Test
    void multiTableStartupModeMustBeConsistent() {
        var factory = new MySqlCdcSourceFactory();
        var metrics = new io.yak.ops.connector.base.metrics.TableMetrics();
        var initial = new TableSourceFactoryContext(options().toReadonlyConfig(), TABLE, SCHEMA, metrics);
        var latest = new TableSourceFactoryContext(
                options().set(MySqlCdcSourceOptions.STARTUP_MODE, "latest-offset").toReadonlyConfig(),
                new TableId("app", null, "items"),
                SCHEMA,
                metrics);
        assertThrows(IllegalArgumentException.class, () -> factory.createSource(List.of(initial, latest)));
    }

    private static Configuration options() {
        return new Configuration().set(JdbcConnectionOptions.URL, "jdbc:mysql://localhost:3307/app")
                .set(JdbcConnectionOptions.USERNAME, "user").set(JdbcConnectionOptions.PASSWORD, "secret")
                .set(MySqlCdcSourceOptions.SERVER_ID, 8123).set(MySqlCdcSourceOptions.TOPIC_PREFIX, "replication_8123");
    }
}
