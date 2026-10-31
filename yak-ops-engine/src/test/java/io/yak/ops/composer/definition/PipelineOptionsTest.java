package io.yak.ops.composer.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.yak.ops.composer.execution.ExecutionPhase;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.composer.validation.PipelineDefinitionValidator;
import io.yak.ops.core.factories.DataSourceFactory;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.composer.validation.SchemaValidator;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;


class PipelineOptionsTest {
    @Test
    void directYamlDefinitionCreatesOneRealtimeRoute() {
        PipelineOptions job = job("""
                source:
                  type: mysql
                  datasource-id: source
                  tables: app.orders
                  scan.startup.mode: initial
                sink:
                  type: jdbc-mysql
                  datasource-id: target
                  yak.namespace-kind: SCHEMA
                  yak.schema.initial: VERIFY
                  yak.table.public.ods_orders.write.mode: UPSERT
                route:
                  - source-table: app.orders
                    sink-table: public.ods_orders
                pipeline:
                  execution.runtime-mode: STREAMING
                  yak.table-scope: SINGLE
                """);
        assertEquals(1, job.tables().size());
        assertEquals(List.of(ExecutionPhase.FULL, ExecutionPhase.INCREMENTAL), job.steps());
        assertEquals("mysql", job.tables().getFirst().source().getSourceMap().get("type"));
        assertEquals("ods_orders", job.tables().getFirst().sink().getSourceMap().get("table"));
    }

    @Test
    void retainsSpecificOffsetOptions() {
        PipelineOptions job = job("""
                source:
                  type: mysql
                  datasource-id: source
                  tables: app.orders
                  scan.startup.mode: specific-offset
                  scan.startup.specific-offset.file: mysql-bin.000001
                  scan.startup.specific-offset.pos: 1234
                sink:
                  type: jdbc-mysql
                  datasource-id: target
                  write.mode: UPSERT
                pipeline:
                  execution.runtime-mode: STREAMING
                """);
        assertEquals(List.of(ExecutionPhase.INCREMENTAL), job.steps());
        assertEquals("specific-offset",
                job.tables().getFirst().source().getSourceMap().get("scan.startup.mode"));
        assertTrue(job.tables().getFirst().source().getSourceMap()
                .containsKey("scan.startup.specific-offset.pos"));
    }

    @Test
    void retainsTransformForTheComposerToTranslate() {
        PipelineOptions options = job("""
                source:
                  type: mysql
                  datasource-id: source
                  tables: app.orders
                sink:
                  type: jdbc-mysql
                  datasource-id: target
                transform:
                  - source-table: app.orders
                    filter: id > 10
                """);
        assertEquals("mysql", options.sourceIdentifier());
        assertEquals("STREAMING", options.runtimeMode());
    }

    @Test
    void expandsUpstreamRoutePatternsAndReplacementSymbolsWithFirstMatch() {
        String yaml = """
                source:
                  type: jdbc-mysql
                  datasource-id: source
                  tables: app.orders,app.customers
                sink:
                  type: jdbc-mysql
                  datasource-id: target
                  write.mode: APPEND
                route:
                  - source-table: app.\\.*
                    sink-table: warehouse.copy_<table>
                    replace-symbol: <table>
                  - source-table: app.orders
                    sink-table: warehouse.another_copy
                pipeline:
                  execution.runtime-mode: BATCH
                  route-mode: FIRST_MATCH
                """;
        PipelineOptions options = job(yaml);
        assertEquals(List.of("copy_orders", "copy_customers"), options.tables().stream()
                .map(table -> table.sink().get(EndpointOptions.TABLE))
                .toList());
        assertThrows(UnsupportedOperationException.class, () -> job(yaml.replace("FIRST_MATCH", "ALL_MATCH")));
    }

    @Test
    void rejectsSchemaMergingBeforeCreatingWriters() {
        assertThrows(UnsupportedOperationException.class, () -> job("""
                source:
                  type: jdbc-mysql
                  datasource-id: source
                  tables: app.orders,app.customers
                sink:
                  type: jdbc-mysql
                  datasource-id: target
                  write.mode: APPEND
                route:
                  - source-table: app.\\.*
                    sink-table: warehouse.combined
                pipeline:
                  execution.runtime-mode: BATCH
                """));
    }

    @Test
    void snapshotOnlyUsesBoundedFactoryContractAndDoesNotRequireChangelogKeys() {
        PipelineDef definition = new YamlPipelineDefinitionParser().parse("""
                source:
                  type: mysql
                  datasource-id: source
                  tables: app.orders
                  scan.startup.mode: snapshot
                  scan.incremental.snapshot.chunk.key-column: id
                sink:
                  type: jdbc-postgres
                  datasource-id: target
                  write.mode: APPEND
                pipeline:
                  execution.runtime-mode: BATCH
                """);
        FactoryUtil factories = new FactoryUtil();
        PipelineOptions options = PipelineDefinitionValidator.validateConfig(definition, factories);
        assertEquals(List.of(ExecutionPhase.FULL), options.steps());
        TableConfig table = options.tables().getFirst();
        DataSourceFactory source = factories.source("mysql");
        TableSchema schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.INTEGER.copy(false))), List.of());
        source.validateSchema(EndpointOptions.connectorOptions(table.source()), schema);
        SchemaValidator.validateCdcWrite(schema, "POSTGRE_SQL", table.source(), table.sink(), null,
                source.producesChangelog(EndpointOptions.connectorOptions(table.source())), false);
        assertThrows(IllegalArgumentException.class, () -> SchemaValidator.validateCdcWrite(
                schema, "POSTGRE_SQL", table.source(), table.sink(), null, true, false));
        assertThrows(TaskPluginException.class, () ->
                PipelineDefinitionValidator.validateConfig(
                        new YamlPipelineDefinitionParser().parse("""
                                source:
                                  type: mysql
                                  datasource-id: source
                                  tables: app.orders
                                  scan.startup.mode: initial
                                sink:
                                  type: jdbc-mysql
                                  datasource-id: target
                                  write.mode: UPSERT
                                pipeline:
                                  execution.runtime-mode: BATCH
                                """), factories));
    }

    @Test
    void globalWriteOptionsApplyToEveryTableAndPerTableKeysOverrideThem() {
        PipelineOptions options = job("""
                source:
                  type: jdbc-mysql
                  datasource-id: source
                  tables: app.orders,app.customers
                sink:
                  type: jdbc-mysql
                  datasource-id: target
                  write.mode: UPSERT
                  write.primary-keys: id
                  yak.table.warehouse.customers_copy.write.primary-keys: tenant_id,id
                route:
                  - source-table: app.orders
                    sink-table: warehouse.orders_copy
                  - source-table: app.customers
                    sink-table: warehouse.customers_copy
                pipeline:
                  execution.runtime-mode: BATCH
                """);
        assertEquals(List.of("id"), options.tables().getFirst().targetPrimaryKeys());
        assertEquals(List.of("tenant_id", "id"), options.tables().get(1).targetPrimaryKeys());
    }

    @Test
    void globalTransactionUsesTheSameXaContractAsPerTableConfiguration() {
        String yaml = """
                source:
                  type: mysql
                  datasource-id: source
                  tables: app.orders
                  scan.startup.mode: initial
                sink:
                  type: jdbc-mysql
                  datasource-id: target
                  write.mode: RETRACT
                  write.transaction.mode: CHECKPOINT_XA
                route:
                  - source-table: app.orders
                    sink-table: warehouse.orders_copy
                pipeline:
                  execution.runtime-mode: STREAMING
                """;
        PipelineOptions global = job(yaml);
        PipelineOptions perTable = job(yaml.replace("write.transaction.mode:",
                "yak.table.warehouse.orders_copy.write.transaction.mode:"));
        assertTrue(global.tables().getFirst().sink().get(JdbcSinkOptions.XA_ENABLED));
        assertEquals(global.tables().getFirst().sink(), perTable.tables().getFirst().sink());
        assertThrows(IllegalArgumentException.class, () -> job(yaml.replace("RETRACT", "UPSERT")));
        assertThrows(IllegalArgumentException.class, () -> job(yaml.replace("CHECKPOINT_XA", "OTHER")));
    }

    @Test
    void rejectsUnknownWrongScopeAndUnselectedTableExtensions() {
        for (String option : List.of(
                "yak.typo: true",
                "yak.schema.initial: CREATE_OR_ALTER",
                "yak.table.app.unknown.chunk-key-column: id",
                "yak.table.app.orders.incremental.lookback-second: 1")) {
            assertThrows(IllegalArgumentException.class, () -> job(batchYaml(option, "")), option);
        }
        for (String option : List.of(
                "yak.schema.evolution.enable: true",
                "yak.sync.mode: FULL",
                "yak.table.warehouse.unknown.write.mode: UPSERT",
                "yak.table.warehouse.orders_copy.write.primary-key: id")) {
            assertThrows(IllegalArgumentException.class, () -> job(batchYaml("", option)), option);
        }
    }

    @Test
    void rejectsInactiveIncrementalFieldsAndInvalidNamespacesBeforeAdmission() {
        assertThrows(IllegalArgumentException.class,
                () -> job(batchYaml("yak.table.app.orders.incremental.column: id", "")));
        assertThrows(IllegalArgumentException.class,
                () -> job(batchYaml("yak.namespace-kind: INVALID", "").replace("app.orders", "app.public.orders")));
        assertThrows(IllegalArgumentException.class,
                () -> job(batchYaml("yak.sync.mode: FULL", "").replace("BATCH", "STREAMING")));
        assertThrows(IllegalArgumentException.class,
                () -> job(batchYaml("", "") + "  parallelism: 4294967297\n"));
    }

    @Test
    void endpointSpecificKeysDoNotDisappearBeforeFactoryValidation() {
        FactoryUtil factories = new FactoryUtil();
        assertThrows(TaskPluginException.class, () -> PipelineDefinitionValidator.validateConfig(
                new YamlPipelineDefinitionParser().parse(batchYaml("write.mode: UPSERT", "")), factories));
        assertThrows(TaskPluginException.class, () -> PipelineDefinitionValidator.validateConfig(
                new YamlPipelineDefinitionParser().parse(batchYaml("", "tables: ignored_table")), factories));
    }

    private static String batchYaml(String sourceOption, String sinkOption) {
        return """
                source:
                  type: jdbc-mysql
                  datasource-id: source
                  tables: app.orders
                  %s
                sink:
                  type: jdbc-mysql
                  datasource-id: target
                  write.mode: APPEND
                  %s
                route:
                  - source-table: app.orders
                    sink-table: warehouse.orders_copy
                pipeline:
                  execution.runtime-mode: BATCH
                """.formatted(sourceOption, sinkOption);
    }

    private static PipelineOptions job(String yaml) {
        return PipelineOptions.from(new YamlPipelineDefinitionParser().parse(yaml));
    }
}
