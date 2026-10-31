package io.yak.ops.composer.yakflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.composer.PipelineContext;
import io.yak.ops.composer.PipelineExecution;
import io.yak.ops.composer.PipelinePlan;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.definition.SourceDef;
import io.yak.ops.composer.execution.IncrementalBatchPreparation;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.factory.SinkFactoryContext;
import io.yak.ops.connector.base.factory.SourceFactoryContext;
import io.yak.ops.connector.base.source.TableMetadataAccessor;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.connector.sink.CancellableSinkWriter;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.dag.Pipeline;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.OperationType;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.core.factories.DataSinkFactory;
import io.yak.ops.core.factories.DataSourceFactory;
import io.yak.ops.core.function.HashFunction;
import io.yak.ops.core.function.HashFunctionProvider;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.sink.DataSink;
import io.yak.ops.core.sink.EventSinkProvider;
import io.yak.ops.core.source.DataSource;
import io.yak.ops.core.source.EventSourceProvider;
import io.yak.ops.core.source.MetadataAccessor;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamEdge;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamNode;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import io.yak.ops.flow.runtime.partitioning.PartitioningEvent;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the admitted YAML-to-Event execution path with independently registered connectors. */
@Timeout(30)
class YakFlowPipelineComposerExecutionTest {
    private static final String WORKSPACE = "workspace";
    private static final TableId SOURCE_TABLE = new TableId("application", "shop", "orders");
    private static final TableId TARGET_TABLE = new TableId(null, "warehouse", "orders_copy");
    private static final TableSchema SOURCE_SCHEMA = new TableSchema(
            List.of(new Column("id", LogicalTypes.INTEGER.copy(false)), new Column("name", LogicalTypes.STRING)),
            List.of("id"));
    private static final TableSchema TARGET_SCHEMA = new TableSchema(
            List.of(new Column("id", LogicalTypes.INTEGER.copy(false)), new Column("label", LogicalTypes.STRING)),
            List.of("id"));
    private static final Option<String> CONNECTION =
            Options.key("fixture.connection").stringType().noDefaultValue();

    @TempDir
    Path stateRoot;

    @Test
    void yamlComposesSqlRoutingAndSinkOwnedPartitioningForNewConnectorIdentifiers() throws Exception {
        Scenario scenario = new Scenario(false, stateRoot);
        PipelineDef definition = definition(false);
        PipelinePlan plan = scenario.composer.plan(definition, scenario.context);
        PipelineExecution execution = scenario.composer.compose(definition, plan, scenario.context);
        try {
            assertEquals(JobStatus.CREATED, execution.getStatus());
            assertFalse(execution.completion().toCompletableFuture().isDone());
            assertEquals(1, plan.resources().size());
            assertEquals(List.of("id", "label"), columnNames(plan.tables().getFirst().writeSourceSchema()));
            scenario.assertNotSubmitted();

            execution.start();
            execution.start();
            assertEquals(JobStatus.FINISHED, execution.completion().toCompletableFuture().get(15, TimeUnit.SECONDS));
            assertTrue(execution.engineJobIdReady().toCompletableFuture().get(1, TimeUnit.SECONDS).isPresent());
            assertEquals(1, scenario.executor.submissions.get());
            assertEquals(1, scenario.sourceCreates.get());
            assertEquals(1, scenario.sinkCreates.get());
            assertEquals(2, scenario.readerCloses.get());
            assertEquals(3, scenario.writerCloses.get());
            assertEquals(3, scenario.finalFlushes.get());
            assertEquals(0, scenario.writerCancellations.get());
            assertEquals(Map.of(
                    3, GenericRowData.of(3, "NAME-3"),
                    4, GenericRowData.of(4, "NAME-4"),
                    5, GenericRowData.of(5, "NAME-5"),
                    6, GenericRowData.of(6, "NAME-6")), scenario.rows);
            assertEquals(Map.of(3, 0, 4, 2, 5, 1, 6, 0), scenario.destinations);
            assertEquals(Map.of(0, 2, 1, 2, 2, 2), scenario.metadataCounts);
            assertEquals(Set.of("0/3", "1/3"), scenario.hashContexts);
            assertEquals(SOURCE_SCHEMA, scenario.sourceContext.tables().getFirst().schema());
            assertEquals(List.of("id", "label"), columnNames(scenario.sinkContext.tables().getFirst().sourceSchema()));
            assertEquals(TARGET_TABLE, scenario.sinkContext.tables().getFirst().targetTable());
            assertGraph(scenario.executor.graph);
        } finally {
            stopIfRunning(execution);
        }
    }

    @Test
    void cancelBeforeStartHasNoConnectionOrConnectorSideEffects() throws Exception {
        Scenario scenario = new Scenario(false, stateRoot);
        PipelineDef definition = definition(false);
        PipelinePlan plan = scenario.composer.plan(definition, scenario.context);
        PipelineExecution execution = scenario.composer.compose(definition, plan, scenario.context);
        CompletionStage<Void> canceled = execution.cancel();
        assertSame(canceled, execution.cancel());
        canceled.toCompletableFuture().get(1, TimeUnit.SECONDS);
        execution.start();
        assertEquals(JobStatus.CANCELED, execution.completion().toCompletableFuture().get(1, TimeUnit.SECONDS));
        assertFalse(execution.engineJobIdReady().toCompletableFuture().get(1, TimeUnit.SECONDS).isPresent());
        scenario.assertNotSubmitted();
    }

    @Test
    void runningCancellationTerminatesTheComposedGraphWithoutAnEndOfInputFlush() throws Exception {
        Scenario scenario = new Scenario(true, stateRoot);
        PipelineDef definition = definition(true);
        PipelineExecution execution = scenario.composer.compose(
                definition, scenario.composer.plan(definition, scenario.context), scenario.context);
        try {
            execution.start();
            assertTrue(scenario.delivered.await(15, TimeUnit.SECONDS), "The real Event graph must deliver its rows");
            assertFalse(execution.completion().toCompletableFuture().isDone());
            CompletionStage<Void> canceled = execution.cancel();
            assertSame(canceled, execution.cancel());
            canceled.toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(JobStatus.CANCELED, execution.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertEquals(2, scenario.readerCloses.get());
            assertEquals(3, scenario.writerCloses.get());
            assertEquals(3, scenario.writerCancellations.get());
            assertEquals(0, scenario.finalFlushes.get());
            assertEquals(1, scenario.executor.submissions.get());
        } finally {
            stopIfRunning(execution);
        }
    }

    @Test
    void changedDatasourceRevisionFailsReadmissionBeforeCredentialsOrRuntimeCreation() throws Exception {
        Scenario scenario = new Scenario(false, stateRoot);
        PipelineDef definition = definition(false);
        PipelinePlan admitted = scenario.composer.plan(definition, scenario.context);
        PipelineExecution execution = scenario.composer.compose(definition, admitted, scenario.context);
        scenario.lookup.sourceRevision.incrementAndGet();
        assertFalse(admitted.sameAdmission(scenario.composer.plan(definition, scenario.context)));
        execution.start();
        assertEquals(JobStatus.FAILED, execution.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertFalse(execution.engineJobIdReady().toCompletableFuture().get(1, TimeUnit.SECONDS).isPresent());
        scenario.assertNotSubmitted();
    }

    @Test
    void connectorSelectionIsPartOfAdmissionEvenWhenPhysicalMetadataIsIdentical() throws Exception {
        Scenario scenario = new Scenario(false, stateRoot);
        PipelineDef definition = definition(false);
        PipelinePlan admitted = scenario.composer.plan(definition, scenario.context);
        PipelineDef changed = new PipelineDef(
                new SourceDef("fixture-alternate-source", null, definition.getSource().getConfig()),
                definition.getSink(), definition.getRoute(), definition.getTransforms(), definition.getUdfs(),
                definition.getModels(), definition.getConfig());
        PipelinePlan replacement = scenario.composer.plan(changed, scenario.context);
        assertEquals(admitted.resources(), replacement.resources());
        assertFalse(admitted.sameAdmission(replacement));
        PipelineExecution execution = scenario.composer.compose(changed, admitted, scenario.context);
        execution.start();
        assertEquals(JobStatus.FAILED, execution.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
        scenario.assertNotSubmitted();
    }

    private static PipelineDef definition(boolean streaming) {
        return new YamlPipelineDefinitionParser().parse("""
                source:
                  type: fixture-native-event-source
                  datasource-id: source
                  tables: shop.orders
                  yak.namespace-kind: SCHEMA
                sink:
                  type: fixture-event-capture
                  datasource-id: target
                route:
                  - source-table: shop.orders
                    sink-table: warehouse.orders_copy
                transform:
                  - source-table: shop.orders
                    projection: "id, UPPER(name) AS label"
                    filter: "id >= 3"
                pipeline:
                  execution.runtime-mode: %s
                  local-time-zone: UTC
                """.formatted(streaming ? "STREAMING" : "BATCH"));
    }

    private static void assertGraph(StreamGraph graph) {
        assertNotNull(graph);
        List<StreamNode> nodes = graph.getTopologicalNodes();
        assertEquals(List.of("data-sync-source", "Transform", "Schema", "PrePartition", "PostPartition", "data-sync-sink"),
                nodes.stream().map(StreamNode::getName).toList());
        assertEquals(List.of(2, 2, 2, 2, 3, 3), nodes.stream().map(StreamNode::getParallelism).toList());
        assertTrue(nodes.stream().allMatch(node -> node.getUid() != null && !node.getUid().isBlank()));
        assertEquals(Event.class, nodes.getFirst().getOutputType());
        assertEquals(PartitioningEvent.class, nodes.get(3).getOutputType());
        assertEquals(Event.class, nodes.get(4).getOutputType());
        assertEquals(Event.class, nodes.getLast().getInputType().orElseThrow());
        assertEquals(List.of(StreamPartitioning.FORWARD, StreamPartitioning.FORWARD, StreamPartitioning.FORWARD,
                        StreamPartitioning.EVENT, StreamPartitioning.FORWARD),
                graph.getStreamEdges().stream().map(StreamEdge::partitioning).toList());
    }

    private static List<String> columnNames(TableSchema schema) {
        return schema.columns().stream().map(Column::name).toList();
    }

    private static void stopIfRunning(PipelineExecution execution) throws Exception {
        if (!execution.completion().toCompletableFuture().isDone()) {
            execution.cancel().toCompletableFuture().get(5, TimeUnit.SECONDS);
            execution.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private static final class Scenario {
        private final boolean streaming;
        private final Lookup lookup = new Lookup();
        private final CapturingExecutor executor = new CapturingExecutor();
        private final AtomicInteger sourceCreates = new AtomicInteger();
        private final AtomicInteger sinkCreates = new AtomicInteger();
        private final AtomicInteger readerCloses = new AtomicInteger();
        private final AtomicInteger writerCloses = new AtomicInteger();
        private final AtomicInteger writerCancellations = new AtomicInteger();
        private final AtomicInteger finalFlushes = new AtomicInteger();
        private final Map<Integer, RowData> rows = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> destinations = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> metadataCounts = new ConcurrentHashMap<>();
        private final Set<String> hashContexts = ConcurrentHashMap.newKeySet();
        private final CountDownLatch delivered = new CountDownLatch(4);
        private final PipelineContext context = new PipelineContext(WORKSPACE, "definition", 1, "instance", false);
        private final YakFlowPipelineComposer composer;
        private volatile SourceFactoryContext sourceContext;
        private volatile SinkFactoryContext sinkContext;

        private Scenario(boolean streaming, Path stateRoot) {
            this.streaming = streaming;
            ReadonlyConfig options = new Configuration()
                    .set(EnvironmentOptions.SOURCE_PARALLELISM, 2)
                    .set(EnvironmentOptions.SINK_PARALLELISM, 3)
                    .set(EnvironmentOptions.CHECKPOINT_INTERVAL, Duration.ofHours(1))
                    .set(EnvironmentOptions.MAX_RESTARTS, 0)
                    .toReadonlyConfig();
            FactoryUtil factories = new FactoryUtil(List.of(new NativeEventSourceFactory(this, "fixture-native-event-source"),
                    new NativeEventSourceFactory(this, "fixture-alternate-source")),
                    List.of(new EventCaptureFactory(this)));
            composer = new YakFlowPipelineComposer(lookup, factories, options,
                    new ExecutionConfigurationFactory(options, stateRoot), Map.of(), Map.of(), executor,
                    new IncrementalBatchPreparation(lookup, (identity, proposals) -> {
                        throw new AssertionError("This source does not use the JDBC cursor path");
                    }));
        }

        private void assertNotSubmitted() {
            assertEquals(0, executor.submissions.get());
            assertEquals(0, lookup.connectionReads.get());
            assertEquals(0, sourceCreates.get());
            assertEquals(0, sinkCreates.get());
        }
    }

    private static final class Lookup implements DataSourceLookupService {
        private final AtomicLong sourceRevision = new AtomicLong(1);
        private final AtomicInteger connectionReads = new AtomicInteger();

        @Override
        public DataSourceInfo dataSource(String workspaceId, String datasourceId) {
            authorize(workspaceId, datasourceId);
            return new DataSourceInfo("MEMORY", "source".equals(datasourceId) ? "application" : null,
                    "source".equals(datasourceId) ? "shop" : "warehouse",
                    revision(datasourceId));
        }

        @Override
        public DataSourceTable table(String workspaceId, String datasourceId, String database, String schema, String table) {
            DataSourceInfo info = dataSource(workspaceId, datasourceId);
            TableId resolved = info.resolveTable(database, schema, table);
            boolean source = "source".equals(datasourceId);
            assertEquals(source ? SOURCE_TABLE : TARGET_TABLE, resolved);
            return new DataSourceTable("MEMORY", resolved, source ? SOURCE_SCHEMA : TARGET_SCHEMA, info.revision());
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId) {
            return connectionOptions(workspaceId, datasourceId, revision(datasourceId));
        }

        @Override
        public ReadonlyConfig connectionOptions(String workspaceId, String datasourceId, long expectedRevision) {
            authorize(workspaceId, datasourceId);
            if (revision(datasourceId) != expectedRevision) {
                throw new IllegalStateException("Datasource revision changed");
            }
            connectionReads.incrementAndGet();
            return ReadonlyConfig.fromMap(Map.of(CONNECTION.key(), "authorized-fixture"));
        }

        private long revision(String datasourceId) {
            return "source".equals(datasourceId) ? sourceRevision.get() : 1;
        }

        private static void authorize(String workspaceId, String datasourceId) {
            if (!WORKSPACE.equals(workspaceId) || !Set.of("source", "target").contains(datasourceId)) {
                throw new SecurityException("Unauthorized fixture datasource");
            }
        }
    }

    private static final class CapturingExecutor implements PipelineExecutor {
        private final AtomicInteger submissions = new AtomicInteger();
        private volatile StreamGraph graph;

        @Override
        public CompletableFuture<JobClient> execute(Pipeline pipeline, Configuration configuration) {
            submissions.incrementAndGet();
            graph = (StreamGraph) pipeline;
            return new EmbeddedPipelineExecutor().execute(pipeline, configuration);
        }
    }

    private static final class NativeEventSourceFactory implements DataSourceFactory {
        private final Scenario scenario;
        private final String identifier;

        private NativeEventSourceFactory(Scenario scenario, String identifier) {
            this.scenario = scenario;
            this.identifier = identifier;
        }

        @Override
        public String identifier() {
            return identifier;
        }

        @Override
        public String datasourceType() {
            return "MEMORY";
        }

        @Override
        public OptionRule optionRule() {
            return OptionRule.builder().build();
        }

        @Override
        public OptionRule creationOptionRule() {
            return OptionRule.builder().required(CONNECTION).build();
        }

        @Override
        public Boundedness boundedness(ReadonlyConfig options) {
            return scenario.streaming ? Boundedness.CONTINUOUS_UNBOUNDED : Boundedness.BOUNDED;
        }

        @Override
        public boolean producesChangelog() {
            return scenario.streaming;
        }

        @Override
        public DataSource createDataSource(Context context) {
            FactoryUtil.validateCreation(context.getFactoryConfiguration(), this);
            SourceFactoryContext resolved = (SourceFactoryContext) context;
            scenario.sourceContext = resolved;
            scenario.sourceCreates.incrementAndGet();
            Source<Event, EventSplit, Integer> source = new NativeEventSource(scenario, resolved.schemas().get(SOURCE_TABLE));
            return new DataSource() {
                @Override
                public EventSourceProvider getEventSourceProvider() {
                    return () -> source;
                }

                @Override
                public MetadataAccessor getMetadataAccessor() {
                    return new TableMetadataAccessor(resolved.schemas());
                }
            };
        }
    }

    private static final class EventCaptureFactory implements DataSinkFactory {
        private final Scenario scenario;

        private EventCaptureFactory(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public String identifier() {
            return "fixture-event-capture";
        }

        @Override
        public String datasourceType() {
            return "MEMORY";
        }

        @Override
        public OptionRule optionRule() {
            return OptionRule.builder().optional(JdbcSinkOptions.WRITE_MODE).build();
        }

        @Override
        public OptionRule creationOptionRule() {
            return OptionRule.builder().include(optionRule()).required(CONNECTION).build();
        }

        @Override
        public void validate(ReadonlyConfig options, boolean sourceProducesChangelog) {}

        @Override
        public void validateSchema(ReadonlyConfig options, TableSchema source, TableSchema target, boolean sourceProducesChangelog) {
            assertEquals(List.of("id", "label"), columnNames(source));
            assertEquals(columnNames(source), columnNames(target));
            assertEquals(List.of("id"), source.primaryKeys());
        }

        @Override
        public DataSink createDataSink(Context context) {
            FactoryUtil.validateCreation(context.getFactoryConfiguration(), this);
            scenario.sinkContext = (SinkFactoryContext) context;
            scenario.sinkCreates.incrementAndGet();
            return new DataSink() {
                @Override
                public EventSinkProvider getEventSinkProvider() {
                    return () -> writerContext -> {
                        assertEquals(3, writerContext.getTaskInfo().getNumberOfParallelSubtasks());
                        return new EventWriter(scenario, writerContext.getTaskInfo().getIndexOfThisSubtask());
                    };
                }

                @Override
                public MetadataApplier getMetadataApplier() {
                    throw new UnsupportedOperationException("Capture sink has no external DDL");
                }

                @Override
                public HashFunctionProvider<DataChangeEvent> getDataChangeEventHashFunctionProvider(int parallelism) {
                    assertEquals(3, parallelism);
                    return (table, schema) -> {
                        assertEquals(TARGET_TABLE, table);
                        assertEquals(List.of("id", "label"), columnNames(schema.toTableSchema()));
                        return new HashFunction<>() {
                            @Override
                            public int hashcode(DataChangeEvent event) {
                                throw new AssertionError("Partitioning must provide the runtime HashContext");
                            }

                            @Override
                            public int hashcode(HashContext context, DataChangeEvent event) {
                                scenario.hashContexts.add(context.getSourceSubtaskIndex() + "/" + context.getDownstreamParallelism());
                                return -event.after().getInt(0);
                            }
                        };
                    };
                }
            };
        }
    }

    private static final class EventWriter implements CancellableSinkWriter<Event> {
        private final Scenario scenario;
        private final int subtask;
        private final java.util.concurrent.atomic.AtomicBoolean canceled = new java.util.concurrent.atomic.AtomicBoolean();
        private Schema schema;

        private EventWriter(Scenario scenario, int subtask) {
            this.scenario = scenario;
            this.subtask = subtask;
        }

        @Override
        public void write(Event event, Context context) {
            if (event instanceof CreateTableEvent create) {
                assertEquals(TARGET_TABLE, create.tableId());
                assertEquals(List.of("id", "label"), columnNames(create.getSchema()));
                if (schema != null) {
                    assertEquals(schema, create.getPhysicalSchema());
                }
                schema = create.getPhysicalSchema();
                scenario.metadataCounts.merge(subtask, 1, Integer::sum);
                return;
            }
            assertNotNull(schema, "Metadata must precede rows on every target partition");
            DataChangeEvent data = (DataChangeEvent) event;
            assertEquals(TARGET_TABLE, data.tableId());
            assertEquals(OperationType.INSERT, data.op());
            int id = data.after().getInt(0);
            assertEquals(Math.floorMod(-id, 3), subtask);
            assertTrue(scenario.rows.putIfAbsent(id, data.after()) == null, "Each source row is routed exactly once");
            scenario.destinations.put(id, subtask);
            scenario.delivered.countDown();
        }

        @Override
        public void flush(boolean endOfInput) {
            if (endOfInput) {
                scenario.finalFlushes.incrementAndGet();
            }
        }

        @Override
        public void cancel() {
            if (canceled.compareAndSet(false, true)) {
                scenario.writerCancellations.incrementAndGet();
            }
        }

        @Override
        public void close() {
            scenario.writerCloses.incrementAndGet();
        }
    }

    private static final class EventSplit implements SourceSplit {
        private final int reader;
        private final int offset;

        private EventSplit(int reader, int offset) {
            this.reader = reader;
            this.offset = offset;
        }

        @Override
        public String splitId() {
            return "reader-" + reader;
        }
    }

    private static final class NativeEventSource implements Source<Event, EventSplit, Integer> {
        private final Scenario scenario;
        private final Schema schema;

        private NativeEventSource(Scenario scenario, Schema schema) {
            this.scenario = scenario;
            this.schema = schema;
        }

        @Override
        public Boundedness getBoundedness() {
            return scenario.streaming ? Boundedness.CONTINUOUS_UNBOUNDED : Boundedness.BOUNDED;
        }

        @Override
        public SplitEnumerator<EventSplit, Integer> createEnumerator(SplitEnumeratorContext<EventSplit> context) {
            return restoreEnumerator(context, 0);
        }

        @Override
        public SplitEnumerator<EventSplit, Integer> restoreEnumerator(SplitEnumeratorContext<EventSplit> context, Integer checkpointState) {
            return new SplitEnumerator<>() {
                private int assigned = checkpointState;

                @Override
                public void start() {
                    context.registeredReaders().keySet().forEach(this::addReader);
                }

                @Override
                public void handleSplitRequest(int subtaskId, String requesterHostname) {
                    addReader(subtaskId);
                }

                @Override
                public void addSplitsBack(List<EventSplit> splits, int subtaskId) {
                    splits.forEach(split -> context.assignSplit(split, subtaskId));
                }

                @Override
                public void addReader(int subtaskId) {
                    if ((assigned & (1 << subtaskId)) == 0) {
                        assigned |= 1 << subtaskId;
                        context.assignSplit(new EventSplit(subtaskId, 0), subtaskId);
                    }
                    context.signalNoMoreSplits(subtaskId);
                }

                @Override
                public Integer snapshotState(long checkpointId) {
                    return assigned;
                }

                @Override
                public void close() {}
            };
        }

        @Override
        public SourceReader<Event, EventSplit> createReader(SourceReaderContext context) {
            assertEquals(2, context.currentParallelism());
            return new SourceReader<>() {
                private EventSplit current;
                private boolean metadataPending = true;
                private boolean noMoreSplits;
                private CompletableFuture<Void> available = new CompletableFuture<>();

                @Override
                public void start() {
                    context.sendSplitRequest();
                }

                @Override
                public InputStatus pollNext(ReaderOutput<Event> output) throws Exception {
                    if (metadataPending) {
                        metadataPending = false;
                        output.collect(new CreateTableEvent(SOURCE_TABLE, context.restoredSourceSchema(SOURCE_TABLE).orElse(schema)));
                    }
                    if (current == null) {
                        return InputStatus.NOTHING_AVAILABLE;
                    }
                    if (current.offset < 3) {
                        int id = current.reader * 3 + current.offset + 1;
                        output.collect(DataChangeEvent.insertEvent(SOURCE_TABLE, GenericRowData.of(id, "name-" + id)));
                        current = new EventSplit(current.reader, current.offset + 1);
                    }
                    if (current.offset < 3) {
                        return InputStatus.MORE_AVAILABLE;
                    }
                    available = new CompletableFuture<>();
                    return !scenario.streaming && noMoreSplits ? InputStatus.END_OF_INPUT : InputStatus.NOTHING_AVAILABLE;
                }

                @Override
                public CompletableFuture<Void> isAvailable() {
                    return metadataPending || current != null && current.offset < 3 || !scenario.streaming && noMoreSplits
                            ? CompletableFuture.completedFuture(null) : available;
                }

                @Override
                public void addSplits(List<EventSplit> splits) {
                    assertEquals(1, splits.size());
                    current = splits.getFirst();
                    available.complete(null);
                }

                @Override
                public void notifyNoMoreSplits() {
                    noMoreSplits = true;
                    available.complete(null);
                }

                @Override
                public List<EventSplit> snapshotState(long checkpointId) {
                    return current == null ? List.of() : List.of(new EventSplit(current.reader, current.offset));
                }

                @Override
                public void close() {
                    scenario.readerCloses.incrementAndGet();
                }
            };
        }

        @Override
        public SimpleVersionedSerializer<EventSplit> getSplitSerializer() {
            return new SimpleVersionedSerializer<>() {
                @Override
                public int getVersion() {
                    return 1;
                }

                @Override
                public byte[] serialize(EventSplit value) {
                    return ByteBuffer.allocate(8).putInt(value.reader).putInt(value.offset).array();
                }

                @Override
                public EventSplit deserialize(int version, byte[] serialized) throws IOException {
                    if (version != 1 || serialized.length != 8) {
                        throw new IOException("Invalid fixture source split state");
                    }
                    ByteBuffer input = ByteBuffer.wrap(serialized);
                    return new EventSplit(input.getInt(), input.getInt());
                }
            };
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            return new SimpleVersionedSerializer<>() {
                @Override
                public int getVersion() {
                    return 1;
                }

                @Override
                public byte[] serialize(Integer value) {
                    return ByteBuffer.allocate(4).putInt(value).array();
                }

                @Override
                public Integer deserialize(int version, byte[] serialized) throws IOException {
                    if (version != 1 || serialized.length != 4) {
                        throw new IOException("Invalid fixture enumerator state");
                    }
                    return ByteBuffer.wrap(serialized).getInt();
                }
            };
        }
    }
}
