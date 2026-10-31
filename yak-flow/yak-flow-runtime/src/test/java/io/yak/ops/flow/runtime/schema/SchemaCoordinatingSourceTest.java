package io.yak.ops.flow.runtime.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
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
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaUtils;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.flow.runtime.checkpoint.CheckpointDeclinedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SchemaCoordinatingSourceTest {
    private static final TableId TABLE = new TableId(null, "shop", "orders");
    private static final Schema BEFORE = new Schema(List.of(new PhysicalColumn("id", LogicalTypes.INTEGER)), List.of());
    private static final Schema AFTER = BEFORE.withColumns(List.of(
            new PhysicalColumn("id", LogicalTypes.INTEGER), new PhysicalColumn("note", LogicalTypes.STRING)));

    @Test
    void nativeMetadataWaitsForWriterAcknowledgementAndFencesAdvancedSourceOffsets() throws Exception {
        AtomicInteger snapshots = new AtomicInteger();
        StubSource delegate = new StubSource((context, output, poll) -> {
            if (poll == 0) {
                output.collect(new CreateTableEvent(TABLE, BEFORE));
                output.collect(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(1)));
                output.collect(addNote());
                output.collect(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(2, "new")));
            }
        }, snapshots);
        SchemaCoordinatingSource<TestSplit, Integer> source = new SchemaCoordinatingSource<>(delegate, Map.of(TABLE, BEFORE));
        assertSame(delegate.getSplitSerializer(), source.getSplitSerializer());
        assertSame(delegate.getEnumeratorCheckpointSerializer(), source.getEnumeratorCheckpointSerializer());
        Context context = new Context(BEFORE, 7);
        List<Event> emitted = new ArrayList<>();
        try (SourceReader<Event, TestSplit> reader = source.createReader(context)) {
            reader.start();
            assertEquals(InputStatus.NOTHING_AVAILABLE, reader.pollNext(emitted::add));
            assertEquals(2, emitted.size());
            assertEquals(1, context.requests.get());
            assertEquals("native-event:8", context.change.eventId());
            assertEquals(BEFORE, context.change.before());
            assertEquals(AFTER, context.change.after());
            assertFalse(reader.isAvailable().isDone());
            assertThrows(CheckpointDeclinedException.class, () -> reader.snapshotState(1));
            assertEquals(0, snapshots.get());

            context.acknowledgement.complete(8L);
            assertTrue(reader.isAvailable().isDone());
            reader.pollNext(emitted::add);
            assertEquals(List.of(CreateTableEvent.class, DataChangeEvent.class, AddColumnEvent.class, DataChangeEvent.class),
                    emitted.stream().map(Object::getClass).toList());
            assertEquals(GenericRowData.of(2, "new"), ((DataChangeEvent) emitted.getLast()).after());
            reader.snapshotState(2);
            assertEquals(1, snapshots.get());
            assertEquals(1, context.requests.get());
        }
    }

    @Test
    void acknowledgedConnectorChangesForwardTheirMetadataSequenceWithoutPlanningDdlAgain() throws Exception {
        Schema changed = new Schema(List.of(new PhysicalColumn("id", LogicalTypes.BIGINT),
                new PhysicalColumn("note", LogicalTypes.STRING)), List.of());
        List<SchemaChangeEvent> metadata = SchemaUtils.diff(TABLE, BEFORE, changed);
        assertEquals(2, metadata.size());
        AtomicReference<CompletableFuture<Long>> pending = new AtomicReference<>();
        StubSource delegate = new StubSource((context, output, poll) -> {
            if (poll == 0) {
                output.collect(new CreateTableEvent(TABLE, BEFORE));
                pending.set(context.sendSourceSchemaChange(new SourceSchemaChange("binlog:44", TABLE, BEFORE, changed))
                        .toCompletableFuture());
            } else if (poll == 1) {
                assertEquals(1L, pending.get().join());
                for (SchemaChangeEvent event : metadata) output.collect(event);
                output.collect(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(9L, "changed")));
            }
        }, new AtomicInteger());
        Context context = new Context(BEFORE, 0);
        List<Event> emitted = new ArrayList<>();
        try (SourceReader<Event, TestSplit> reader = new SchemaCoordinatingSource<>(delegate, Map.of(TABLE, BEFORE)).createReader(context)) {
            reader.start();
            reader.pollNext(emitted::add);
            context.acknowledgement.complete(1L);
            reader.pollNext(emitted::add);
            assertEquals(4, emitted.size());
            assertEquals(metadata, emitted.subList(1, 3));
            assertEquals(1, context.requests.get());
            assertEquals("binlog:44", context.change.eventId());
            reader.snapshotState(1);
        }
    }

    @Test
    void catalogDriftIsReplacedOnlyWhenItMatchesTheAdmittedRecoveryMetadata() throws Exception {
        StubSource delegate = new StubSource((context, output, poll) -> {
            if (poll == 0) {
                output.collect(new CreateTableEvent(TABLE, AFTER));
                output.collect(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(1)));
            }
        }, new AtomicInteger());
        List<Event> events = new ArrayList<>();
        try (SourceReader<Event, TestSplit> reader = new SchemaCoordinatingSource<>(delegate, Map.of(TABLE, AFTER))
                .createReader(new Context(BEFORE, 0))) {
            reader.start();
            reader.pollNext(events::add);
            assertEquals(BEFORE, ((CreateTableEvent) events.getFirst()).getPhysicalSchema());
            assertEquals(2, events.size());
        }

        Schema wrong = new Schema(List.of(new PhysicalColumn("id", LogicalTypes.STRING)), List.of());
        StubSource mismatched = new StubSource((context, output, poll) -> output.collect(new CreateTableEvent(TABLE, wrong)),
                new AtomicInteger());
        try (SourceReader<Event, TestSplit> reader = new SchemaCoordinatingSource<>(mismatched, Map.of(TABLE, BEFORE))
                .createReader(new Context(BEFORE, 0))) {
            reader.start();
            assertThrows(IllegalStateException.class, () -> reader.pollNext(events::add));
        }
    }

    @Test
    void retainedEventsDetachReusedRowsAndKeepChangeIdentity() throws Exception {
        GenericRowData reused = GenericRowData.of(1);
        ChangeEventId identity = new ChangeEventId("source", "event", 0, 1);
        StubSource delegate = new StubSource((context, output, poll) -> {
            if (poll == 0) {
                output.collect(new CreateTableEvent(TABLE, BEFORE));
                output.collect(DataChangeEvent.insertEvent(TABLE, reused, Map.of("origin", "fixture")).withChangeEventId(identity));
                reused.setField(0, 2);
                output.collect(DataChangeEvent.insertEvent(TABLE, reused));
                reused.setField(0, 3);
            }
        }, new AtomicInteger());
        List<Event> events = new ArrayList<>();
        try (SourceReader<Event, TestSplit> reader = new SchemaCoordinatingSource<>(delegate, Map.of(TABLE, BEFORE))
                .createReader(new Context(BEFORE, 0))) {
            reader.start();
            reader.pollNext(events::add);
            DataChangeEvent first = (DataChangeEvent) events.get(1);
            assertEquals(GenericRowData.of(1), first.after());
            assertEquals(identity, first.changeEventId());
            assertEquals(Map.of("origin", "fixture"), first.meta());
            assertEquals(GenericRowData.of(2), ((DataChangeEvent) events.get(2)).after());
        }
    }

    @Test
    void pendingSchemaAcknowledgementDoesNotPreventReaderCancellation() throws Exception {
        StubSource delegate = new StubSource((context, output, poll) -> {
            output.collect(new CreateTableEvent(TABLE, BEFORE));
            output.collect(addNote());
        }, new AtomicInteger());
        Context context = new Context(BEFORE, 0);
        SourceReader<Event, TestSplit> reader = new SchemaCoordinatingSource<>(delegate, Map.of(TABLE, BEFORE)).createReader(context);
        reader.start();
        reader.pollNext(event -> {});
        assertFalse(context.acknowledgement.isDone());
        reader.close();
        assertEquals(1, delegate.closed.get());
    }

    @Test
    void parallelNativeMetadataFailsBeforeCoordinatingDdlOrReleasingFollowingRows() throws Exception {
        StubSource delegate = new StubSource((context, output, poll) -> {
            if (poll == 0) {
                output.collect(new CreateTableEvent(TABLE, BEFORE));
                output.collect(addNote());
                output.collect(DataChangeEvent.insertEvent(TABLE, GenericRowData.of(2, "blocked")));
            }
        }, new AtomicInteger());
        for (int subtask = 0; subtask < 2; subtask++) {
            Context context = new Context(BEFORE, 0);
            context.parallelism = 2;
            context.subtask = subtask;
            List<Event> emitted = new ArrayList<>();
            try (SourceReader<Event, TestSplit> reader = new SchemaCoordinatingSource<>(delegate, Map.of(TABLE, BEFORE)).createReader(context)) {
                reader.start();
                assertThrows(UnsupportedOperationException.class, () -> reader.pollNext(emitted::add));
                assertEquals(1, emitted.size());
                assertTrue(emitted.getFirst() instanceof CreateTableEvent);
                assertEquals(0, context.requests.get());
            }
        }
        assertEquals(2, delegate.closed.get());
    }

    private static AddColumnEvent addNote() {
        return new AddColumnEvent(TABLE, List.of(AddColumnEvent.last(new PhysicalColumn("note", LogicalTypes.STRING))));
    }

    private static final class Context implements SourceReaderContext {
        private final Schema recovered;
        private final long version;
        private final AtomicInteger requests = new AtomicInteger();
        private final CompletableFuture<Long> acknowledgement = new CompletableFuture<>();
        private SourceSchemaChange change;
        private int parallelism = 1;
        private int subtask;

        private Context(Schema recovered, long version) {
            this.recovered = recovered;
            this.version = version;
        }

        @Override public Configuration getConfiguration() { return new Configuration(); }
        @Override public int getIndexOfSubtask() { return subtask; }
        @Override public int currentParallelism() { return parallelism; }
        @Override public void sendSplitRequest() {}
        @Override public Optional<Schema> restoredSourceSchema(TableId table) { return Optional.of(recovered); }
        @Override public long sourceSchemaVersion(TableId table) { return version; }
        @Override public CompletionStage<Long> sendSourceSchemaChange(SourceSchemaChange request) {
            change = request;
            requests.incrementAndGet();
            return acknowledgement;
        }
    }

    @FunctionalInterface
    private interface Poll {
        void emit(SourceReaderContext context, ReaderOutput<Event> output, int invocation) throws Exception;
    }

    private static final class TestSplit implements SourceSplit {
        @Override public String splitId() { return "test"; }
    }

    private static final class StubSource implements Source<Event, TestSplit, Integer> {
        private final Poll poll;
        private final AtomicInteger snapshots;
        private final AtomicInteger closed = new AtomicInteger();
        private final SimpleVersionedSerializer<TestSplit> splits = new SimpleVersionedSerializer<>() {
            @Override public int getVersion() { return 1; }
            @Override public byte[] serialize(TestSplit value) { return new byte[] {1}; }
            @Override public TestSplit deserialize(int version, byte[] value) { return new TestSplit(); }
        };
        private final SimpleVersionedSerializer<Integer> enumerator = new SimpleVersionedSerializer<>() {
            @Override public int getVersion() { return 1; }
            @Override public byte[] serialize(Integer value) { return new byte[] {value.byteValue()}; }
            @Override public Integer deserialize(int version, byte[] value) { return (int) value[0]; }
        };

        private StubSource(Poll poll, AtomicInteger snapshots) { this.poll = poll; this.snapshots = snapshots; }
        @Override public Boundedness getBoundedness() { return Boundedness.CONTINUOUS_UNBOUNDED; }
        @Override public SplitEnumerator<TestSplit, Integer> createEnumerator(SplitEnumeratorContext<TestSplit> context) { throw new UnsupportedOperationException(); }
        @Override public SplitEnumerator<TestSplit, Integer> restoreEnumerator(SplitEnumeratorContext<TestSplit> context, Integer state) { throw new UnsupportedOperationException(); }
        @Override public SimpleVersionedSerializer<TestSplit> getSplitSerializer() { return splits; }
        @Override public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() { return enumerator; }

        @Override
        public SourceReader<Event, TestSplit> createReader(SourceReaderContext context) {
            return new SourceReader<>() {
                private int invocation;
                @Override public void start() {}
                @Override public InputStatus pollNext(ReaderOutput<Event> output) throws Exception {
                    poll.emit(context, output, invocation++);
                    return InputStatus.NOTHING_AVAILABLE;
                }
                @Override public CompletableFuture<Void> isAvailable() { return new CompletableFuture<>(); }
                @Override public void addSplits(List<TestSplit> splits) {}
                @Override public void notifyNoMoreSplits() {}
                @Override public List<TestSplit> snapshotState(long checkpointId) { snapshots.incrementAndGet(); return List.of(new TestSplit()); }
                @Override public void close() { closed.incrementAndGet(); }
            };
        }
    }
}
