package io.yak.ops.connector.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.SourceFactoryContext;
import io.yak.ops.connector.base.factory.SinkFactoryContext;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.jdbc.source.MySqlDataSourceFactory;
import io.yak.ops.connector.jdbc.sink.MySqlDataSinkFactory;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.sink.SchemaEvolutionSink;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.factories.DataSourceFactory;
import io.yak.ops.core.source.DataSource;
import io.yak.ops.core.sink.DataSink;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Connector creation accepts only its own authorized configuration and metadata. */
class JdbcFactoryCreationTest {
    private static final TableId TABLE = new TableId("app", null, "items");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));

    @Test
    void buildsSourceAndSinkWithoutPlatformIdentityOrOpeningConnections() {
        Configuration configuration = configuration();
        TableSourceFactoryContext source = new TableSourceFactoryContext(configuration.toReadonlyConfig(), TABLE, SCHEMA);
        DataSource created = new MySqlDataSourceFactory().createDataSource(sourceContext(source));
        assertEquals(Boundedness.BOUNDED, created.getEventSourceProvider().getSource().getBoundedness());
        assertEquals(List.of(TABLE), created.getMetadataAccessor().listTables("app", null));
        assertEquals(SCHEMA, created.getMetadataAccessor().getTableSchema(TABLE).toTableSchema());
        configuration.set(JdbcSinkOptions.WRITE_MODE, JdbcWriteMode.APPEND);
        TableSinkFactoryContext sink = new TableSinkFactoryContext(configuration.toReadonlyConfig(), TABLE, SCHEMA, TABLE, SCHEMA);
        DataSink written = new MySqlDataSinkFactory().createDataSink(sinkContext(sink));
        assertInstanceOf(SchemaEvolutionSink.class, written.getEventSinkProvider().getSink());
        assertFalse(source.toString().contains("private-password"));
        assertFalse(sink.toString().contains("private-password"));
    }

    @Test
    void directSinkCreationChecksRealRowIdentityBeforeConnecting() {
        Configuration configuration = configuration().set(JdbcSinkOptions.WRITE_MODE, JdbcWriteMode.UPSERT);
        TableSchema keyless = new TableSchema(SCHEMA.columns(), List.of());
        MySqlDataSinkFactory factory = new MySqlDataSinkFactory();
        assertThrows(IllegalArgumentException.class, () -> factory.createDataSink(sinkContext(new TableSinkFactoryContext(
                configuration.toReadonlyConfig(), TABLE, keyless, TABLE, SCHEMA))));
        assertThrows(IllegalArgumentException.class, () -> factory.createDataSink(sinkContext(new TableSinkFactoryContext(
                configuration.toReadonlyConfig(), TABLE, SCHEMA, TABLE, keyless))));
        configuration.set(JdbcSinkOptions.WRITE_MODE, JdbcWriteMode.APPEND);
        factory.createDataSink(sinkContext(new TableSinkFactoryContext(
                configuration.toReadonlyConfig(), TABLE, keyless, TABLE, keyless)));
    }

    @Test
    void requiresResolvedConnectionInputsAndRejectsUnknownFields() {
        MySqlDataSourceFactory factory = new MySqlDataSourceFactory();
        TableSourceFactoryContext missing = new TableSourceFactoryContext(ReadonlyConfig.fromMap(Map.of()), TABLE, SCHEMA);
        assertThrows(IllegalArgumentException.class, () -> factory.createDataSource(sourceContext(missing)));
        TableSourceFactoryContext unknown = new TableSourceFactoryContext(ReadonlyConfig.fromMap(
                Map.of(JdbcConnectionOptions.URL.key(), "jdbc:mysql://localhost/app", "unregistered", true)), TABLE, SCHEMA);
        assertThrows(IllegalArgumentException.class, () -> factory.createDataSource(sourceContext(unknown)));
        TableSourceFactoryContext mismatched = new TableSourceFactoryContext(
                configuration().set(JdbcConnectionOptions.URL, "jdbc:oracle:thin:@localhost/test").toReadonlyConfig(), TABLE, SCHEMA);
        assertThrows(IllegalArgumentException.class, () -> factory.createDataSource(sourceContext(mismatched)));
    }

    @Test
    void discoversIndependentConnectorIdentifiersWithoutLegacyAliases() {
        FactoryUtil factories = new FactoryUtil();
        assertEquals("MYSQL", factories.source("jdbc-mysql").datasourceType());
        assertEquals("POSTGRE_SQL", factories.source("jdbc-postgres").datasourceType());
        assertEquals("ORACLE", factories.sink("jdbc-oracle").datasourceType());
        assertTrue(factories.sourceFactories().stream().map(DataSourceFactory::identifier).toList().contains("jdbc-mysql"));
        assertThrows(IllegalArgumentException.class, () -> factories.source("JDBC"));
        assertThrows(IllegalArgumentException.class, () -> factories.source("MYSQL_CDC"));
        assertThrows(IllegalArgumentException.class, () -> factories.sink("jdbc-mysql").validateDatasource("ORACLE"));
    }

    private static Configuration configuration() {
        return new Configuration().set(JdbcConnectionOptions.URL, "jdbc:mysql://localhost/app")
                .set(JdbcConnectionOptions.USERNAME, "user").set(JdbcConnectionOptions.PASSWORD, "private-password")
                .set(JdbcConnectionOptions.DRIVER_CLASS, "driver.must.not.be.loaded.WhileCreating");
    }

    private static SourceFactoryContext sourceContext(TableSourceFactoryContext context) {
        return new SourceFactoryContext(List.of(context), new Configuration(), JdbcFactoryCreationTest.class.getClassLoader());
    }

    private static SinkFactoryContext sinkContext(TableSinkFactoryContext context) {
        return new SinkFactoryContext(List.of(context), new Configuration(), JdbcFactoryCreationTest.class.getClassLoader());
    }
}
