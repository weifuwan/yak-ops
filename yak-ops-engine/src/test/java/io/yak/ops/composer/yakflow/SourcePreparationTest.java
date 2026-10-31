package io.yak.ops.composer.yakflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.composer.PipelineContext;
import io.yak.ops.composer.PipelineExecution;
import io.yak.ops.composer.PipelinePlan;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.execution.IncrementalBatchPreparation;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.SourceFactoryContext;
import io.yak.ops.connector.base.source.TableMetadataAccessor;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.factories.DataSinkFactory;
import io.yak.ops.core.factories.DataSourceFactory;
import io.yak.ops.core.sink.DataSink;
import io.yak.ops.core.source.DataSource;
import io.yak.ops.core.source.EventSourceProvider;
import io.yak.ops.core.source.MetadataAccessor;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Source-owned constraints and application limits are checked before target-side preparation. */
@Timeout(15)
class SourcePreparationTest {
    private static final TableId SOURCE = new TableId("shop", null, "orders");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(new Column("id", LogicalTypes.INTEGER.copy(false))), List.of("id"));
    private static final PipelineContext CONTEXT = new PipelineContext("workspace", "definition", 1, "instance", false);

    @TempDir
    Path stateDirectory;

    @Test
    void sourceRejectsParallelismBeforeAnyTargetDdlOrSinkCreation() throws Exception {
        Scenario scenario = new Scenario(stateDirectory);
        PipelineDef definition = definition(2);
        PipelinePlan plan = scenario.composer.plan(definition, CONTEXT);
        assertFalse(plan.tables().getFirst().schemaChanges().isEmpty(), "The admitted plan must actually require DDL");
        assertEquals(0, scenario.lookup.connectionReads.get());
        PipelineExecution execution = scenario.composer.compose(definition, plan, CONTEXT);
        execution.start();
        assertEquals(JobStatus.FAILED, execution.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(1, scenario.source.creations.get());
        assertEquals(1, scenario.source.parallelismChecks.get());
        assertEquals(0, scenario.sink.metadataCreations.get());
        assertEquals(0, scenario.sink.ddlApplications.get());
        assertEquals(0, scenario.sink.writerCreations.get());
        assertEquals(0, scenario.submissions.get());
        assertTrue(execution.engineJobIdReady().toCompletableFuture().get().isEmpty());
    }

    @Test
    void pipelineOverrideCannotBypassEnvironmentLimitsOrReadCredentialsDuringPlan() {
        Scenario scenario = new Scenario(stateDirectory);
        assertThrows(IllegalArgumentException.class, () -> scenario.composer.plan(definition(17), CONTEXT));
        assertEquals(0, scenario.lookup.metadataReads.get());
        assertEquals(0, scenario.lookup.connectionReads.get());
        assertEquals(0, scenario.source.creations.get());
        assertEquals(0, scenario.sink.metadataCreations.get());
    }

    private static PipelineDef definition(int parallelism) {
        return new YamlPipelineDefinitionParser().parse("""
                source:
                  type: single-reader
                  datasource-id: source
                  tables: shop.orders
                  yak.namespace-kind: DATABASE
                sink:
                  type: schema-observer
                  datasource-id: target
                  yak.namespace-kind: DATABASE
                  yak.schema.initial: CREATE_OR_ALTER
                route:
                  - source-table: shop.orders
                    sink-table: warehouse.orders_copy
                pipeline:
                  execution.runtime-mode: BATCH
                  local-time-zone: UTC
                  parallelism: %d
                """.formatted(parallelism));
    }

    private static final class Scenario {
        private final Lookup lookup = new Lookup();
        private final SingleReaderFactory source = new SingleReaderFactory();
        private final SchemaObserverFactory sink = new SchemaObserverFactory();
        private final AtomicInteger submissions = new AtomicInteger();
        private final YakFlowPipelineComposer composer;

        private Scenario(Path state) {
            ReadonlyConfig environment = EnvironmentOptions.defaults();
            composer = new YakFlowPipelineComposer(lookup, new FactoryUtil(List.of(source), List.of(sink)), environment,
                    new ExecutionConfigurationFactory(environment, state), Map.of(), Map.of(), (pipeline, config) -> {
                        submissions.incrementAndGet();
                        throw new AssertionError("Invalid source parallelism must prevent runtime submission");
                    }, new IncrementalBatchPreparation(lookup, (identity, proposed) -> {
                        throw new AssertionError("This full batch does not use cursors");
                    }));
        }
    }

    private static final class Lookup implements DataSourceLookupService {
        private final AtomicInteger metadataReads = new AtomicInteger();
        private final AtomicInteger connectionReads = new AtomicInteger();

        @Override
        public DataSourceInfo dataSource(String workspaceId, String datasourceId) {
            metadataReads.incrementAndGet();
            return new DataSourceInfo("MYSQL", "source".equals(datasourceId) ? "shop" : "warehouse", null, 1);
        }

        @Override
        public DataSourceTable table(String workspaceId, String datasourceId, String database, String schema, String table) {
            metadataReads.incrementAndGet();
            if (!"source".equals(datasourceId)) throw new AssertionError("Target readback must not be reached");
            return new DataSourceTable("MYSQL", SOURCE, SCHEMA, 1);
        }

        @Override
        public Optional<DataSourceTable> findTable(String workspaceId, String datasourceId, String database, String schema, String table) {
            metadataReads.incrementAndGet();
            return Optional.empty();
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId) {
            return connectionOptions(workspaceId, datasourceId, 1);
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId, long expectedRevision) {
            connectionReads.incrementAndGet();
            return ReadonlyConfig.fromMap(Map.of());
        }
    }

    private static final class SingleReaderFactory implements DataSourceFactory {
        private final AtomicInteger creations = new AtomicInteger();
        private final AtomicInteger parallelismChecks = new AtomicInteger();

        @Override public String identifier() { return "single-reader"; }
        @Override public String datasourceType() { return "MYSQL"; }
        @Override public OptionRule optionRule() { return OptionRule.builder().build(); }
        @Override public Boundedness boundedness(ReadonlyConfig options) { return Boundedness.BOUNDED; }
        @Override public boolean producesChangelog() { return false; }
        @Override public boolean supportsSchemaMetadata() { return true; }

        @Override
        public DataSource createDataSource(Context context) {
            creations.incrementAndGet();
            SourceFactoryContext resolved = (SourceFactoryContext) context;
            return new DataSource() {
                @Override public MetadataAccessor getMetadataAccessor() { return new TableMetadataAccessor(resolved.schemas()); }
                @Override public EventSourceProvider getEventSourceProvider() { return () -> new SingleReaderSource(parallelismChecks); }
            };
        }
    }

    private static final class SingleReaderSource implements Source<Event, SourceSplit, Integer> {
        private final AtomicInteger parallelismChecks;
        private SingleReaderSource(AtomicInteger parallelismChecks) { this.parallelismChecks = parallelismChecks; }
        @Override public Boundedness getBoundedness() { return Boundedness.BOUNDED; }
        @Override public void validateParallelism(int parallelism) {
            parallelismChecks.incrementAndGet();
            Source.super.validateParallelism(parallelism);
            if (parallelism != 1) throw new IllegalArgumentException("This source requires one reader");
        }
        @Override public SplitEnumerator<SourceSplit, Integer> createEnumerator(SplitEnumeratorContext<SourceSplit> context) { throw new AssertionError("Source must remain unstarted"); }
        @Override public SplitEnumerator<SourceSplit, Integer> restoreEnumerator(SplitEnumeratorContext<SourceSplit> context, Integer state) { throw new AssertionError("Source must remain unstarted"); }
        @Override public SourceReader<Event, SourceSplit> createReader(SourceReaderContext context) { throw new AssertionError("Source must remain unstarted"); }
        @Override public SimpleVersionedSerializer<SourceSplit> getSplitSerializer() { throw new AssertionError("Source must remain unstarted"); }
        @Override public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() { throw new AssertionError("Source must remain unstarted"); }
    }

    private static final class SchemaObserverFactory implements DataSinkFactory {
        private final AtomicInteger metadataCreations = new AtomicInteger();
        private final AtomicInteger ddlApplications = new AtomicInteger();
        private final AtomicInteger writerCreations = new AtomicInteger();
        @Override public String identifier() { return "schema-observer"; }
        @Override public String datasourceType() { return "MYSQL"; }
        @Override public OptionRule optionRule() { return OptionRule.builder().build(); }
        @Override public boolean supportsSchemaChanges() { return true; }
        @Override public void validate(ReadonlyConfig options, boolean changelog) {}
        @Override public void validateSchema(ReadonlyConfig options, TableSchema source, TableSchema target, boolean changelog) { assertEquals(source, target); }
        @Override public MetadataApplier createMetadataApplier(ReadonlyConfig options) {
            metadataCreations.incrementAndGet();
            return event -> ddlApplications.incrementAndGet();
        }
        @Override public DataSink createDataSink(Context context) {
            writerCreations.incrementAndGet();
            throw new AssertionError("Invalid Source must prevent Sink creation");
        }
    }
}
