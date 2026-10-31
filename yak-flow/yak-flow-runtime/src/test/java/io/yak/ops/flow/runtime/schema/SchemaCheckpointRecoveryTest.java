package io.yak.ops.flow.runtime.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.sink.SchemaChangeAwareSinkWriter;
import io.yak.ops.core.api.connector.sink.SchemaEvolutionSink;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.core.configuration.SchemaEvolutionOptions;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeEventType;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaUtils;
import io.yak.ops.core.schema.SchemaVersionedTableState;
import io.yak.ops.core.sink.DefaultDataChangeEventHashFunctionProvider;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.checkpoint.LocalCompletedCheckpointStore;
import io.yak.ops.flow.runtime.checkpoint.SchemaManagerSnapshotSerializer;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import io.yak.ops.flow.runtime.partitioning.PartitioningEvent;
import io.yak.ops.flow.runtime.partitioning.PostPartitionProcessor;
import io.yak.ops.flow.runtime.partitioning.RegularPrePartitionOperator;
import io.yak.ops.flow.runtime.transformations.OneInputTransformation;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Real source offsets, schema coordination, partitioning and durable checkpoint publication. */
@Timeout(30)
class SchemaCheckpointRecoveryTest {
    private static final TableId SOURCE = new TableId("source", null, "orders");
    private static final TableId TARGET = new TableId("target", null, "orders");
    private static final Schema BEFORE = new Schema(
            List.of(new PhysicalColumn("id", LogicalTypes.INTEGER.copy(false))), List.of("id"));
    private static final Schema AFTER = BEFORE.withColumns(List.of(new PhysicalColumn("id", LogicalTypes.INTEGER.copy(false)),
            new PhysicalColumn("note", LogicalTypes.STRING)));

    @TempDir
    Path stateDirectory;

    @Test
    void preDdlCheckpointRestoresRawSchemaAndOldRowsAfterTheLiveCatalogHasAdvanced() throws Exception {
        Target target = new Target();
        ScriptedSource firstSource = new ScriptedSource(BEFORE, false, 1);
        EvolutionSink firstSink = new EvolutionSink(target, BEFORE);
        Configuration firstConfig = configuration(false);
        StreamGraph firstGraph = graph(firstSource, firstSink, firstConfig);
        JobClient first = new EmbeddedPipelineExecutor().execute(firstGraph, firstConfig).get(5, TimeUnit.SECONDS);
        try {
            firstSource.checkpoint.get(10, TimeUnit.SECONDS);
            assertEquals(0, target.ddl.get());
            assertEquals(Map.of(1, GenericRowData.of(1)), target.rows);
        } finally {
            cancel(first);
        }
        CheckpointSnapshot snapshot = latest(firstGraph, firstConfig);
        SchemaManagerSnapshot.TableState baseline = schemas(snapshot).tables().getFirst();
        assertEquals(0, baseline.version());
        assertEquals(BEFORE, baseline.sourceSchema());
        assertEquals(BEFORE, baseline.targetSchema());

        // The new Source definition observes a catalog that has advanced past the saved offset.
        // Its restored split still contains one old-shaped row before the native ADD_COLUMN.
        ScriptedSource restoredSource = new ScriptedSource(AFTER, true, 4);
        EvolutionSink restoredSink = new EvolutionSink(target, AFTER);
        Configuration restoreConfig = configuration(true);
        StreamGraph restoredGraph = graph(restoredSource, restoredSink, restoreConfig);
        JobClient restored = new EmbeddedPipelineExecutor().execute(restoredGraph, restoreConfig).get(5, TimeUnit.SECONDS);
        try {
            restoredSource.checkpoint.get(10, TimeUnit.SECONDS);
            assertEquals(0, restoredSink.restored.getFirst().version());
            assertEquals(BEFORE, restoredSink.restored.getFirst().sourceSchema());
            assertEquals(List.of(BEFORE, BEFORE, BEFORE), restoredSink.createdSchemas);
            assertEquals(1, restoredSource.restoredOffset.get());
            assertEquals(1, target.ddl.get());
            assertEquals(AFTER, target.physicalSchema);
            assertEquals(Map.of(1, GenericRowData.of(1), 2, GenericRowData.of(2), 3, GenericRowData.of(3, "added")), target.rows);
            assertEquals(3, restoredSink.adoptedVersions.size());
            assertTrue(restoredSink.adoptedVersions.stream().allMatch(version -> version == 1));
        } finally {
            cancel(restored);
        }
        SchemaManagerSnapshot.TableState completed = schemas(latest(restoredGraph, restoreConfig)).tables().getFirst();
        assertEquals(1, completed.version());
        assertEquals(AFTER, completed.sourceSchema());
        assertEquals(AFTER, completed.targetSchema());
        assertTrue(completed.appliedEventFingerprints().containsKey("native-event:1"));
        assertEquals(1, firstSource.closed.get());
        assertEquals(1, restoredSource.closed.get());
        assertEquals(6, target.closedWriters.get());
    }

    @Test
    void checkpointsWithoutInitialSchemaRoutesFailBeforeOpeningReadersWritersOrMetadataResources() throws Exception {
        Target target = new Target();
        ScriptedSource firstSource = new ScriptedSource(BEFORE, false, 1);
        Configuration config = configuration(false);
        StreamGraph firstGraph = graph(firstSource, new EvolutionSink(target, BEFORE), config);
        JobClient first = new EmbeddedPipelineExecutor().execute(firstGraph, config).get(5, TimeUnit.SECONDS);
        try {
            firstSource.checkpoint.get(10, TimeUnit.SECONDS);
        } finally {
            cancel(first);
        }
        CheckpointSnapshot completed = latest(firstGraph, config);
        SchemaManagerSnapshotSerializer serializer = new SchemaManagerSnapshotSerializer();
        CheckpointSnapshot withoutBaseline = new CheckpointSnapshot(completed.checkpointId() + 1, completed.graphSignature(),
                completed.enumeratorState(), completed.readerSplits(), completed.assignments(), completed.completedAtMillis(),
                completed.operatorStates(), completed.fullyFinishedOperators(), completed.finishedSubtasks(),
                new CheckpointSnapshot.SerializedState(serializer.getVersion(), serializer.serialize(new SchemaManagerSnapshot(List.of()))));
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(stateDirectory)) {
            store.save(withoutBaseline);
        }

        ScriptedSource newSource = new ScriptedSource(AFTER, true, 4);
        EvolutionSink newSink = new EvolutionSink(target, AFTER);
        Configuration restore = configuration(true);
        StreamGraph graph = graph(newSource, newSink, restore);
        JobClient rejected = new EmbeddedPipelineExecutor().execute(graph, restore).get(5, TimeUnit.SECONDS);
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> rejected.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertEquals(JobStatus.FAILED, rejected.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, newSource.created.get());
        assertEquals(0, newSink.writers.get());
        assertEquals(0, newSink.appliers.get());
        assertEquals(0, target.ddl.get());
    }

    private Configuration configuration(boolean restore) {
        return new Configuration()
                .set(CheckpointingOptions.STATE_DIRECTORY, stateDirectory.toString())
                .set(CheckpointingOptions.DEFINITION_IDENTITY, "schema-recovery")
                .set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofMillis(30))
                .set(CheckpointingOptions.CHECKPOINTING_TIMEOUT, Duration.ofSeconds(5))
                .set(CheckpointingOptions.RESTORE_LATEST, restore)
                .set(SchemaEvolutionOptions.ENABLED, true)
                .set(ExecutionOptions.MAX_RESTART_ATTEMPTS, 0)
                .set(PipelineOptions.AUTO_GENERATE_UIDS, false);
    }

    private static StreamGraph graph(ScriptedSource source, EvolutionSink sink, Configuration config) {
        SourceTransformation<Event> input = new SourceTransformation<>("source",
                new SchemaCoordinatingSource<>(source, Map.of(SOURCE, source.catalogSchema)), Event.class, 1);
        input.setUid("schema-source");
        OneInputTransformation<Event, Event> route = new OneInputTransformation<>(input, "route", () -> (event, output) -> {
            if (event instanceof SchemaChangeEvent schema) output.collect(schema.copy(TARGET));
            else output.collect(DataChangeEvent.route((DataChangeEvent) event, TARGET));
        }, Event.class, 1);
        route.setUid("schema-route");
        OneInputTransformation<Event, PartitioningEvent> pre = new OneInputTransformation<>(route, "pre-partition",
                () -> new RegularPrePartitionOperator(3, new DefaultDataChangeEventHashFunctionProvider()), PartitioningEvent.class, 1);
        pre.setUid("schema-pre-partition");
        OneInputTransformation<PartitioningEvent, Event> post = new OneInputTransformation<>(pre, "post-partition",
                PostPartitionProcessor::new, Event.class, 3);
        post.setInputPartitioning(StreamPartitioning.EVENT);
        post.setUid("schema-post-partition");
        SinkTransformation<Event> output = new SinkTransformation<>(post, "sink", sink, 3);
        output.setUid("schema-sink");
        return new StreamGraphGenerator(output, config).generate();
    }

    private CheckpointSnapshot latest(StreamGraph graph, Configuration config) throws IOException {
        try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(stateDirectory)) {
            return store.loadLatest(LocalCompletedCheckpointStore.graphSignature(graph,
                    config.get(PipelineOptions.MAX_PARALLELISM), config.get(CheckpointingOptions.DEFINITION_IDENTITY))).orElseThrow();
        }
    }

    private static SchemaManagerSnapshot schemas(CheckpointSnapshot snapshot) throws IOException {
        return new SchemaManagerSnapshotSerializer().deserialize(snapshot.schemaManagerState().version(), snapshot.schemaManagerState().bytes());
    }

    private static void cancel(JobClient client) throws Exception {
        client.cancel().get(5, TimeUnit.SECONDS);
        assertThrows(java.util.concurrent.ExecutionException.class, () -> client.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertEquals(JobStatus.CANCELED, client.getJobStatus().get(5, TimeUnit.SECONDS));
    }

    private static final class Target {
        private final Map<Integer, RowData> rows = new ConcurrentHashMap<>();
        private final AtomicInteger ddl = new AtomicInteger();
        private final AtomicInteger closedWriters = new AtomicInteger();
        private volatile Schema physicalSchema = BEFORE;
    }

    private static final class EvolutionSink implements Sink<Event>, SchemaEvolutionSink {
        private final Target target;
        private final Schema admittedSource;
        private final Schema admittedTarget;
        private final AtomicInteger writers = new AtomicInteger();
        private final AtomicInteger appliers = new AtomicInteger();
        private final List<Schema> createdSchemas = new CopyOnWriteArrayList<>();
        private final List<Long> adoptedVersions = new CopyOnWriteArrayList<>();
        private volatile Schema sourceSchema;
        private volatile Schema targetSchema;
        private volatile List<SchemaVersionedTableState> restored;

        private EvolutionSink(Target target, Schema admittedSource) {
            this.target = target;
            this.admittedSource = admittedSource;
            admittedTarget = target.physicalSchema;
            sourceSchema = admittedSource;
            targetSchema = admittedTarget;
        }

        @Override public List<SchemaVersionedTableState> initialSchemaState() {
            return List.of(new SchemaVersionedTableState(SOURCE, TARGET, admittedSource, admittedTarget, 0));
        }

        @Override public void restoreSchemaState(List<SchemaVersionedTableState> states) {
            restored = List.copyOf(states);
            sourceSchema = states.getFirst().sourceSchema();
            targetSchema = states.getFirst().targetSchema();
        }

        @Override public synchronized SchemaChangeRequest planSchemaChange(SourceSchemaChange change, boolean allowDropColumns) {
            assertFalse(allowDropColumns);
            assertEquals(sourceSchema, change.before());
            return new SchemaChangeRequest(change.eventId(), SOURCE, TARGET, change.before(), change.after(), targetSchema,
                    change.after(), SchemaUtils.diff(TARGET, targetSchema, change.after()));
        }

        @Override public synchronized void schemaChangeCompleted(SchemaChangeRequest request) {
            sourceSchema = request.sourceSchemaAfter();
            targetSchema = request.targetSchemaAfter();
        }

        @Override public MetadataApplier createMetadataApplier() {
            appliers.incrementAndGet();
            return new MetadataApplier() {
                @Override public MetadataApplier setAcceptedSchemaEvolutionTypes(Set<SchemaChangeEventType> types) {
                    assertEquals(Set.of(SchemaChangeEventType.ADD_COLUMN), types);
                    return this;
                }
                @Override public void applySchemaChange(SchemaChangeEvent event) {
                    assertEquals(TARGET, event.tableId());
                    target.physicalSchema = SchemaUtils.applySchemaChangeEvent(target.physicalSchema, event);
                    target.ddl.incrementAndGet();
                }
            };
        }

        @Override public SinkWriter<Event> createWriter(WriterInitContext context) {
            writers.incrementAndGet();
            return new EvolutionWriter(this, sourceSchema);
        }
    }

    private static final class EvolutionWriter implements SinkWriter<Event>, SchemaChangeAwareSinkWriter<Event> {
        private final EvolutionSink owner;
        private final List<DataChangeEvent> buffered = new ArrayList<>();
        private Schema schema;
        private Schema announced;

        private EvolutionWriter(EvolutionSink owner, Schema schema) { this.owner = owner; this.schema = schema; }

        @Override public void write(Event event, Context context) {
            if (event instanceof CreateTableEvent create) {
                announced = create.getPhysicalSchema();
                owner.createdSchemas.add(announced);
                assertEquals(schema, announced);
            } else if (event instanceof SchemaChangeEvent change) {
                announced = SchemaUtils.applySchemaChangeEvent(announced, change);
                assertEquals(schema, announced);
            } else {
                DataChangeEvent change = (DataChangeEvent) event;
                assertEquals(TARGET, change.tableId());
                assertEquals(schema, announced);
                assertEquals(schema.columns().size(), change.after().getArity());
                buffered.add(change);
            }
        }

        @Override public void flush(boolean endOfInput) {
            for (DataChangeEvent row : buffered) {
                assertTrue(owner.target.rows.putIfAbsent(row.after().getInt(0), row.after()) == null);
            }
            buffered.clear();
        }

        @Override public void validateSchemaChange(SchemaChangeRequest request) {
            assertEquals(schema, request.sourceSchemaBefore());
        }

        @Override public void onSchemaChange(SchemaChangeRequest request, long version) {
            assertEquals(request.targetSchemaAfter(), owner.target.physicalSchema);
            schema = request.sourceSchemaAfter();
            owner.adoptedVersions.add(version);
        }

        @Override public void close() { owner.target.closedWriters.incrementAndGet(); }
    }

    private static final class OffsetSplit implements SourceSplit {
        private final int offset;
        private OffsetSplit(int offset) { this.offset = offset; }
        @Override public String splitId() { return "binlog"; }
    }

    private static final class ScriptedSource implements Source<Event, OffsetSplit, Integer> {
        private final Schema catalogSchema;
        private final boolean releaseDdl;
        private final int expectedCheckpointOffset;
        private final CompletableFuture<Long> checkpoint = new CompletableFuture<>();
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        private final AtomicInteger restoredOffset = new AtomicInteger(-1);
        private final List<Event> events = List.of(
                DataChangeEvent.insertEvent(SOURCE, GenericRowData.of(1)),
                DataChangeEvent.insertEvent(SOURCE, GenericRowData.of(2)),
                new AddColumnEvent(SOURCE, List.of(AddColumnEvent.last(new PhysicalColumn("note", LogicalTypes.STRING)))),
                DataChangeEvent.insertEvent(SOURCE, GenericRowData.of(3, "added")));

        private ScriptedSource(Schema catalogSchema, boolean releaseDdl, int expectedCheckpointOffset) {
            this.catalogSchema = catalogSchema;
            this.releaseDdl = releaseDdl;
            this.expectedCheckpointOffset = expectedCheckpointOffset;
        }

        @Override public Boundedness getBoundedness() { return Boundedness.CONTINUOUS_UNBOUNDED; }
        @Override public SplitEnumerator<OffsetSplit, Integer> createEnumerator(SplitEnumeratorContext<OffsetSplit> context) {
            return restoreEnumerator(context, 0);
        }
        @Override public SplitEnumerator<OffsetSplit, Integer> restoreEnumerator(SplitEnumeratorContext<OffsetSplit> context, Integer state) {
            return new SplitEnumerator<>() {
                private boolean assigned = state == 1;
                @Override public void start() {}
                @Override public void handleSplitRequest(int subtaskId, String hostname) {}
                @Override public void addSplitsBack(List<OffsetSplit> splits, int subtaskId) { splits.forEach(split -> context.assignSplit(split, subtaskId)); }
                @Override public void addReader(int subtaskId) {
                    if (!assigned) { assigned = true; context.assignSplit(new OffsetSplit(0), subtaskId); }
                    context.signalNoMoreSplits(subtaskId);
                }
                @Override public Integer snapshotState(long checkpointId) { return assigned ? 1 : 0; }
                @Override public void close() {}
            };
        }

        @Override public SourceReader<Event, OffsetSplit> createReader(SourceReaderContext context) {
            created.incrementAndGet();
            return new SourceReader<>() {
                private OffsetSplit current;
                private boolean metadata = true;
                private boolean started;
                private int snapshottedOffset;
                private final CompletableFuture<Void> waiting = new CompletableFuture<>();
                @Override public void start() { started = true; }
                @Override public InputStatus pollNext(ReaderOutput<Event> output) throws Exception {
                    if (metadata) { metadata = false; output.collect(new CreateTableEvent(SOURCE, catalogSchema)); }
                    while (current != null && current.offset < (releaseDdl ? events.size() : 1)) {
                        output.collect(events.get(current.offset));
                        current = new OffsetSplit(current.offset + 1);
                    }
                    return InputStatus.NOTHING_AVAILABLE;
                }
                @Override public CompletableFuture<Void> isAvailable() {
                    return metadata || current != null && current.offset < (releaseDdl ? events.size() : 1)
                            ? CompletableFuture.completedFuture(null) : waiting;
                }
                @Override public void addSplits(List<OffsetSplit> splits) {
                    current = splits.getFirst();
                    if (!started) restoredOffset.set(current.offset);
                }
                @Override public void notifyNoMoreSplits() {}
                @Override public List<OffsetSplit> snapshotState(long checkpointId) {
                    snapshottedOffset = current == null ? 0 : current.offset;
                    return current == null ? List.of() : List.of(new OffsetSplit(current.offset));
                }
                @Override public void notifyCheckpointComplete(long checkpointId) {
                    if (snapshottedOffset >= expectedCheckpointOffset) checkpoint.complete(checkpointId);
                }
                @Override public void close() { closed.incrementAndGet(); }
            };
        }

        @Override public SimpleVersionedSerializer<OffsetSplit> getSplitSerializer() {
            return new SimpleVersionedSerializer<>() {
                @Override public int getVersion() { return 1; }
                @Override public byte[] serialize(OffsetSplit value) { return ByteBuffer.allocate(4).putInt(value.offset).array(); }
                @Override public OffsetSplit deserialize(int version, byte[] value) throws IOException {
                    if (version != 1 || value.length != 4) throw new IOException("Invalid offset fixture");
                    return new OffsetSplit(ByteBuffer.wrap(value).getInt());
                }
            };
        }
        @Override public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            return new SimpleVersionedSerializer<>() {
                @Override public int getVersion() { return 1; }
                @Override public byte[] serialize(Integer value) { return new byte[] {value.byteValue()}; }
                @Override public Integer deserialize(int version, byte[] value) throws IOException {
                    if (version != 1 || value.length != 1) throw new IOException("Invalid enumerator fixture");
                    return (int) value[0];
                }
            };
        }
    }
}
