package io.yak.ops.composer.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.composer.PipelineContext;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.execution.IncrementalBatchPreparation;
import io.yak.ops.composer.yakflow.EnvironmentOptions;
import io.yak.ops.composer.yakflow.ExecutionConfigurationFactory;
import io.yak.ops.composer.yakflow.YakFlowPipelineComposer;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.TaskValidationException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** RETRACT admission rejects expressions that cannot reconstruct a previously written full row. */
class RetractionTransformValidationTest {
    private static final TableSchema SOURCE = new TableSchema(List.of(
            new Column("bucket", LogicalTypes.BIGINT.copy(false)), new Column("amount", LogicalTypes.BIGINT)), List.of());
    private static final TableSchema TARGET = new TableSchema(List.of(
            new Column("bucket", LogicalTypes.BIGINT.copy(false)), new Column("stamp", LogicalTypes.BIGINT)), List.of());
    private static final PipelineContext CONTEXT = new PipelineContext("ws", "task", 1, "instance", false);
    private final FactoryUtil factories = new FactoryUtil();
    private final Lookup lookup = new Lookup();

    @TempDir
    Path stateRoot;

    @Test
    void publicationAndComposerRejectUnstableRetractionBeforeRuntimeCredentials() {
        YakFlowPipelineComposer composer = composer();
        for (PipelineDef definition : List.of(
                definition("bucket, UNIX_TIMESTAMP() AS stamp", null),
                definition("bucket, amount AS stamp", "__data_event_type__ = '+I'"))) {
            PipelineDefinitionValidator.validateConfig(definition, factories);
            TaskValidationException publication = assertThrows(TaskValidationException.class,
                    () -> PipelineDefinitionValidator.validateResources(definition, lookup, factories));
            assertEquals("SCHEMA_VALIDATION", publication.stage());
            assertEquals("逐行更新的转换和筛选必须只依赖来源行及固定表信息，不能使用当前时间、随机值或事件类型", publication.publicMessage());
            IllegalArgumentException execution = assertThrows(IllegalArgumentException.class,
                    () -> composer.plan(definition, CONTEXT));
            assertEquals("RETRACT transforms require deterministic expressions without event-dependent metadata", execution.getMessage());
        }
    }

    @Test
    void deterministicRetractionRemainsAdmissibleThroughBothEntrypoints() {
        PipelineDef definition = definition("bucket, amount + 1 AS stamp", "amount > 0");
        assertDoesNotThrow(() -> PipelineDefinitionValidator.validateResources(definition, lookup, factories));
        assertEquals(TARGET, composer().plan(definition, CONTEXT).tables().getFirst().writeSourceSchema());
    }

    private YakFlowPipelineComposer composer() {
        ReadonlyConfig options = EnvironmentOptions.defaults();
        return new YakFlowPipelineComposer(lookup, factories, options,
                new ExecutionConfigurationFactory(options, stateRoot), Map.of(), Map.of(),
                (pipeline, configuration) -> CompletableFuture.failedFuture(new AssertionError("Admission only")),
                new IncrementalBatchPreparation(lookup, (identity, proposals) -> {
                    throw new AssertionError("CDC does not use a JDBC incremental cursor");
                }));
    }

    private static PipelineDef definition(String projection, String filter) {
        return new YamlPipelineDefinitionParser().parse("""
                source:
                  type: mysql
                  datasource-id: source
                  tables: db.orders
                  scan.startup.mode: initial
                  scan.incremental.snapshot.chunk.key-column: bucket
                sink:
                  type: jdbc-mysql
                  datasource-id: sink
                  yak.namespace-kind: DATABASE
                  write.mode: RETRACT
                  write.transaction.mode: CHECKPOINT_XA
                route:
                  - source-table: db.orders
                    sink-table: db.result
                transform:
                  - source-table: db.orders
                    projection: %s
                %s
                pipeline:
                  execution.runtime-mode: STREAMING
                  local-time-zone: UTC
                """.formatted(projection, filter == null ? "" : "    filter: \"" + filter + "\""));
    }

    private static final class Lookup implements DataSourceLookupService, TaskValidationContext {
        @Override
        public DataSourceInfo dataSource(String id) {
            return new DataSourceInfo("MYSQL", "db", null, 1);
        }

        @Override
        public DataSourceInfo dataSource(String workspace, String id) {
            return dataSource(id);
        }

        @Override
        public TableSchema tableSchema(String id, TableId table) {
            return "source".equals(id) ? SOURCE : TARGET;
        }

        @Override
        public DataSourceTable table(String workspace, String id, String database, String schema, String table) {
            TableId identity = new TableId(database, schema, table);
            return new DataSourceTable("MYSQL", identity, tableSchema(id, identity), 1);
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspace, String id) {
            throw new AssertionError("Admission must not open runtime connections");
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspace, String id, long revision) {
            return connectionOptions(workspace, id);
        }
    }
}
