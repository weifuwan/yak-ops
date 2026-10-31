package io.yak.ops.composer.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.composer.PipelineContext;
import io.yak.ops.composer.PipelinePlan;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.definition.PipelineOptions;
import io.yak.ops.composer.definition.TransformDef;
import io.yak.ops.composer.yakflow.EnvironmentOptions;
import io.yak.ops.composer.yakflow.ExecutionConfigurationFactory;
import io.yak.ops.composer.yakflow.YakFlowPipelineComposer;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.task.api.execution.TaskCursorBatch;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.apache.flink.cdc.common.pipeline.DecimalPrecisionMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Composer-produced cursor bindings pin row semantics independently of admission and instance IDs. */
class IncrementalBatchPreparationTest {
    private static final String URL = "jdbc:mysql:cursor-semantic-fixture";
    private static final TableSchema SHAPE = new TableSchema(
            List.of(new Column("id", LogicalTypes.BIGINT.copy(false)), new Column("name", LogicalTypes.STRING)),
            List.of("id"));

    @TempDir
    Path stateRoot;

    private final Driver driver = new UpperBoundDriver();
    private final List<TaskCursorBatch> proposals = new ArrayList<>();
    private IncrementalBatchPreparation preparation;
    private YakFlowPipelineComposer composer;

    @BeforeEach
    void createComposerAndSourceUpperBound() throws Exception {
        DriverManager.registerDriver(driver);
        DataSourceLookupService lookup = new Lookup();
        preparation = new IncrementalBatchPreparation(lookup, (identity, batches) -> {
            proposals.addAll(batches);
            return batches.stream()
                    .map(batch -> new TaskCursorBatch(
                            batch.tableKey(), batch.binding(), batch.mode(), batch.column(), batch.initialValue(),
                            "100", batch.upper(), batch.allowInitialLoad(), false, 1))
                    .toList();
        });
        ReadonlyConfig options = EnvironmentOptions.defaults();
        composer = new YakFlowPipelineComposer(
                lookup, new FactoryUtil(), options, new ExecutionConfigurationFactory(options, stateRoot),
                Map.of(), Map.of(),
                (pipeline, configuration) -> CompletableFuture.failedFuture(new AssertionError("Planning only")),
                preparation);
    }

    @AfterEach
    void removeUpperBoundDriver() throws Exception {
        DriverManager.deregisterDriver(driver);
    }

    @Test
    void changedTransformSemanticsCannotKeepTheSamePersistentCursorBinding() throws Exception {
        TaskCursorBatch original = prepare(definition("id, name", "id < 50"), 1);
        List<PipelineDef> changed = new ArrayList<>();
        changed.add(definition("id, name", "id < 150"));
        changed.add(definition("id, LOWER(name) AS name", "id < 50"));
        PipelineDef timezone = definition("id, name", "id < 50");
        timezone.getConfig().set(org.apache.flink.cdc.common.pipeline.PipelineOptions.PIPELINE_LOCAL_TIME_ZONE, "GMT+08:00");
        changed.add(timezone);
        PipelineDef precision = definition("id, name", "id < 50");
        precision.getConfig().set(org.apache.flink.cdc.common.pipeline.PipelineOptions.PIPELINE_TRANSFORM_DECIMAL_PRECISION_MODE,
                DecimalPrecisionMode.UP_TO_38);
        changed.add(precision);
        PipelineDef primaryKeys = definition("id, name", "id < 50");
        primaryKeys.getTransforms().set(0, new TransformDef("db.orders", "id, name", "id < 50", "id", null, null, null, null));
        changed.add(primaryKeys);
        for (PipelineDef definition : changed) {
            TaskCursorBatch replacement = prepare(definition, 2);
            assertEquals(original.tableKey(), replacement.tableKey());
            assertNotEquals(original.binding(), replacement.binding());
        }
    }

    @Test
    void newVersionAndPresentationChangesContinueTheSameSemanticCursor() throws Exception {
        PipelineDef first = definition("id, name", "id < 50");
        PipelinePlan admitted = composer.plan(first, new PipelineContext("ws", "task", 1, "first", false));
        TaskCursorBatch original = prepare(first, 1);
        PipelineDef second = definition("id ,  name", "id<50");
        second.getConfig().set(org.apache.flink.cdc.common.pipeline.PipelineOptions.PIPELINE_NAME, "A new display name");
        second.getTransforms().set(0, new TransformDef(
                "db.orders", "id ,  name", "id<50", null, null, null, "New rule description", null));
        second.getTransforms().add(new TransformDef("db.other", "id", "id > 0", null, null, null, null, null));
        second.getTransforms().add(new TransformDef("db.orders", "id, name", "id > 1000", null, null, null, null, null));
        PipelinePlan replacement = composer.plan(second, new PipelineContext("ws", "task", 2, "second", false));
        assertFalse(admitted.sameAdmission(replacement));
        TaskCursorBatch continued = prepare(second, 2);
        assertEquals(original.tableKey(), continued.tableKey());
        assertEquals(original.binding(), continued.binding());
    }

    private TaskCursorBatch prepare(PipelineDef definition, int version) throws Exception {
        String instance = "instance-" + proposals.size();
        PipelinePlan plan = composer.plan(definition, new PipelineContext("ws", "task", version, instance, false));
        IncrementalBatchPreparation.IncrementalRun run = preparation.prepare(
                plan, PipelineOptions.from(definition), new TaskExecutionIdentity("ws", "task", version, instance));
        assertFalse(run.initialLoad());
        assertEquals("100", run.plan().tables().getFirst().sourceOptions().get(JdbcSourceOptions.RANGE_LOWER));
        assertEquals("200", run.plan().tables().getFirst().sourceOptions().get(JdbcSourceOptions.RANGE_UPPER));
        assertEquals(plan.tables().getFirst().transformRevision(), run.plan().tables().getFirst().transformRevision());
        return proposals.getLast();
    }

    private static PipelineDef definition(String projection, String filter) {
        return new YamlPipelineDefinitionParser().parse("""
                source:
                  type: jdbc-mysql
                  datasource-id: source
                  tables: db.orders
                  yak.namespace-kind: DATABASE
                  yak.sync.mode: FULL_AND_INCREMENTAL
                  yak.table.db.orders.incremental.mode: INCREASING
                  yak.table.db.orders.incremental.column: id
                  yak.table.db.orders.incremental.initial-value: 0
                sink:
                  type: jdbc-mysql
                  datasource-id: sink
                  yak.namespace-kind: DATABASE
                  write.mode: UPSERT
                route:
                  - source-table: db.orders
                    sink-table: db.result
                transform:
                  - source-table: db.orders
                    projection: %s
                    filter: %s
                pipeline:
                  execution.runtime-mode: BATCH
                  local-time-zone: UTC
                """.formatted(projection, filter));
    }

    private static final class Lookup implements DataSourceLookupService {
        @Override
        public DataSourceInfo dataSource(String workspace, String id) {
            return new DataSourceInfo("MYSQL", "db", null, 1);
        }

        @Override
        public DataSourceTable table(String workspace, String id, String database, String schema, String table) {
            return new DataSourceTable("MYSQL", new TableId(database, schema, table), SHAPE, 1);
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspace, String id) {
            return ReadonlyConfig.fromMap(Map.of("connection.url", URL));
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspace, String id, long revision) {
            return connectionOptions(workspace, id);
        }
    }

    /** The test exercises production preparation while supplying only a deterministic MAX(id) result. */
    private static final class UpperBoundDriver implements Driver {
        @Override
        public Connection connect(String url, Properties properties) {
            if (!acceptsURL(url)) return null;
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "prepareStatement" -> Proxy.newProxyInstance(getClass().getClassLoader(),
                                new Class<?>[] {PreparedStatement.class}, (statement, call, parameters) -> switch (call.getName()) {
                                    case "executeQuery" -> Proxy.newProxyInstance(getClass().getClassLoader(),
                                            new Class<?>[] {ResultSet.class}, (result, read, columns) -> switch (read.getName()) {
                                                case "next" -> true;
                                                case "getBigDecimal" -> BigDecimal.valueOf(200);
                                                case "close" -> null;
                                                default -> throw new AssertionError(read.getName());
                                            });
                                    case "close", "setQueryTimeout" -> null;
                                    default -> throw new AssertionError(call.getName());
                                });
                        case "close" -> null;
                        default -> throw new AssertionError(method.getName());
                    });
        }

        @Override
        public boolean acceptsURL(String url) {
            return URL.equals(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }
}
