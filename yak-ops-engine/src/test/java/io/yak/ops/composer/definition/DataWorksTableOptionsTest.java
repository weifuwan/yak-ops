package io.yak.ops.composer.definition;

import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.SinkFactoryContext;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.connector.jdbc.sink.MySqlDataSinkFactory;
import io.yak.ops.connector.jdbc.sink.OracleDataSinkFactory;
import io.yak.ops.connector.jdbc.sink.PostgresDataSinkFactory;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.factories.DataSinkFactory;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataWorksTableOptionsTest {
    @Test
    void batchReadAndWriteOptionsReachTheConnectorAndCannotLeakIntoCdc() {
        String yaml = """
                source:
                  type: jdbc-mysql
                  datasource-id: source
                  tables: app.orders
                  yak.table.app.orders.read.where: id > 10 OR id = 1
                  yak.table.app.orders.read.split-key: id
                  yak.table.app.orders.read.fetch-size: 27
                  yak.table.app.orders.read.query-timeout-seconds: 11
                sink:
                  type: jdbc-mysql
                  datasource-id: target
                  yak.table.app.orders.write.pre-sql: '["DELETE FROM app.orders"]'
                  yak.table.app.orders.write.post-sql: '["UPDATE app.marker SET ready = 1"]'
                  yak.table.app.orders.write.batch-size: 19
                pipeline:
                  execution.runtime-mode: BATCH
                """;
        TableConfig config = options(yaml).tables().getFirst();
        assertEquals("id > 10 OR id = 1", config.source().getSourceMap().get("read.where"));
        assertEquals("id", config.source().getSourceMap().get("read.split-key"));
        assertEquals("27", config.source().getSourceMap().get("connector.jdbc.source.result-set-fetch-size"));
        assertEquals(19L, config.sink().getSourceMap().get("write.batch-size"));
        assertEquals("[\"DELETE FROM app.orders\"]", config.sink().getSourceMap().get("write.pre-sql"));
        assertThrows(IllegalArgumentException.class, () -> options(yaml.replace("type: jdbc-mysql", "type: mysql")
                .replace("execution.runtime-mode: BATCH", "execution.runtime-mode: STREAMING")));
    }

    @Test
    void regexKeepsInitialOverridesAndHasIndependentDefaultsForFutureTables() {
        PipelineOptions config = options(dynamic());
        assertEquals("old_name", config.tables().getFirst().sink().get(EndpointOptions.TABLE));
        assertEquals("false", config.tables().getFirst().source().getSourceMap().get("snapshot.skip"));
        DynamicTableSelection selection = config.dynamicSelection();
        assertEquals("true", selection.sourceDefaults().get("snapshot.skip"));
        assertEquals(new TableId("warehouse", null, "new_orders_2"), selection.target(new TableId("app", null, "orders_2")));
        assertEquals(JdbcWriteMode.UPSERT, ReadonlyConfig.fromMap(selection.sinkDefaults()).get(JdbcSinkOptions.WRITE_MODE));
        assertThrows(IllegalArgumentException.class, () -> selection.target(new TableId("private", null, "orders_2")));
        PipelineOptions anchored = options(dynamic()
                .replace("database-pattern: app", "database-pattern: ^(?:app)$")
                .replace("table-pattern: orders.*", "table-pattern: ^orders.*$"));
        assertTrue(anchored.dynamicSelection().matches(new TableId("app", null, "orders_2")));
        assertEquals("^orders.*$", anchored.tables().getFirst().source().getSourceMap().get("table-name"));
        assertThrows(IllegalArgumentException.class,
                () -> options(dynamic().replace("database-pattern: app", "database-pattern: " + "a".repeat(257))));
    }

    @Test
    void discoveredTableDefaultsPassRealJdbcFactoryCreationAndSchemaValidation() {
        DynamicTableSelection selection = options(dynamic().replace("yak.mapping.write.mode: UPSERT",
                "yak.mapping.write.mode: UPSERT\n  yak.mapping.write.batch-size: 17")).dynamicSelection();
        TableSchema schema = new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        TableId source = new TableId("app", null, "orders_2");
        TableId target = selection.target(source);
        for (DataSinkFactory factory : List.of(new MySqlDataSinkFactory(), new PostgresDataSinkFactory(), new OracleDataSinkFactory())) {
            String url = switch (factory.identifier()) {
                case "jdbc-mysql" -> "jdbc:mysql://localhost/warehouse";
                case "jdbc-postgres" -> "jdbc:postgresql://localhost/warehouse";
                default -> "jdbc:oracle:thin:@localhost:1521/FREEPDB1";
            };
            Map<String, Object> values = new LinkedHashMap<>(selection.sinkDefaults());
            values.putAll(new JdbcConnectionOptions(url, "test", "test").configuration().getSourceMap());
            ReadonlyConfig settings = ReadonlyConfig.fromMap(values);
            FactoryUtil.validateCreation(settings, factory);
            factory.validateSchema(settings, schema, schema, true);
            assertEquals(17, settings.get(JdbcSinkOptions.BATCH_SIZE));
            assertFalse(settings.get(JdbcSinkOptions.XA_ENABLED));
            assertNotNull(factory.createDataSink(new SinkFactoryContext(
                    List.of(new TableSinkFactoryContext(settings, source, schema, target, schema)),
                    new Configuration(), getClass().getClassLoader(), (table, raw) -> raw,
                    Map.of(source, Schema.from(schema, null, Map.of(), Map.of())), true)));
        }
        assertThrows(IllegalArgumentException.class, () -> options(dynamic().replace("yak.mapping.write.mode: UPSERT",
                "yak.mapping.write.mode: UPSERT\n  yak.mapping.write.primary-keys: id")));
        assertThrows(IllegalArgumentException.class, () -> options(dynamic().replace("yak.mapping.write.mode: UPSERT",
                "yak.mapping.write.mode: UPSERT\n  yak.mapping.write.transaction.mode: CHECKPOINT_XA")));
    }

    @Test
    void regexAndSnapshotConstraintsFailBeforeBuildingAnExecution() {
        assertThrows(IllegalArgumentException.class, () -> options(dynamic().replace("scan.startup.mode: initial", "scan.startup.mode: latest-offset")));
        assertThrows(IllegalArgumentException.class, () -> options(dynamic().replace("CREATE_OR_ALTER", "VERIFY")));
        assertThrows(IllegalArgumentException.class, () -> options(dynamic().replace("yak.schema.evolution.enabled: true", "yak.schema.evolution.enabled: false")));
        assertThrows(IllegalArgumentException.class, () -> options(dynamic().replace("yak.selection.mode: REGEX", "yak.selection.mode: EXPLICIT")));
        assertThrows(IllegalArgumentException.class, () -> options(dynamic().replace("yak.table.app.orders.snapshot.skip: false", "yak.table.app.orders.snapshot.where: WHERE id > 1")));
    }

    private static PipelineOptions options(String yaml) {
        return PipelineOptions.from(new YamlPipelineDefinitionParser().parse(yaml));
    }

    private static String dynamic() {
        return """
                source:
                  type: mysql
                  datasource-id: source
                  tables: app.orders
                  scan.startup.mode: initial
                  yak.selection.mode: REGEX
                  yak.selection.database-pattern: app
                  yak.selection.table-pattern: orders.*
                  yak.selection.snapshot.skip: true
                  yak.table.app.orders.snapshot.skip: false
                sink:
                  type: jdbc-mysql
                  datasource-id: target
                  yak.namespace-kind: DATABASE
                  yak.schema.initial: CREATE_OR_ALTER
                  yak.schema.evolution.enabled: true
                  yak.mapping.database: warehouse
                  yak.mapping.table: new_${table}
                  yak.mapping.write.mode: UPSERT
                route:
                  - source-table: app.orders
                    sink-table: warehouse.old_name
                pipeline:
                  execution.runtime-mode: STREAMING
                  yak.table-scope: DATABASE
                """;
    }
}
