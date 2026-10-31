package io.yak.ops.connector.base.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.OperationType;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.LogicalTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TableEventSourceTest {
    private static final TableId TABLE = new TableId("app", null, "orders");
    private static final Schema BEFORE = new Schema(
            List.of(new PhysicalColumn("id", LogicalTypes.INTEGER.copy(false))), List.of("id"));
    private static final Schema AFTER = BEFORE.withColumns(List.of(
            BEFORE.columns().getFirst(), new PhysicalColumn("description", LogicalTypes.varchar(32))));

    @Test
    void emitsOneUpdateAfterMetadataAndKeepsSourceStateSerializers() throws Exception {
        ChangeEventId first = new ChangeEventId("source", "offset", 0, 2);
        FakeSource delegate = new FakeSource((output, context) -> {
            output.collect(new TableRecord(TABLE, RowKind.UPDATE_BEFORE, GenericRowData.of(1), first));
            output.collect(new TableRecord(TABLE, RowKind.UPDATE_AFTER, GenericRowData.of(2),
                    new ChangeEventId("source", "offset", 1, 2)));
        });
        TableEventSource<TestSplit, String> source = new TableEventSource<>(delegate, Map.of(TABLE, BEFORE));
        SourceReader<Event, TestSplit> reader = source.createReader(context(null, new AtomicInteger()));
        List<Event> events = new ArrayList<>();
        reader.pollNext(events::add);
        assertEquals(2, events.size());
        assertInstanceOf(CreateTableEvent.class, events.getFirst());
        DataChangeEvent change = assertInstanceOf(DataChangeEvent.class, events.getLast());
        assertEquals(OperationType.UPDATE, change.op());
        assertEquals(1, change.before().getInt(0));
        assertEquals(2, change.after().getInt(0));
        assertEquals(first, change.changeEventId());
        assertSame(delegate.splitSerializer, source.getSplitSerializer());
        assertSame(delegate.stateSerializer, source.getEnumeratorCheckpointSerializer());
        assertSame(delegate.splits, reader.snapshotState(42));
        reader.notifyCheckpointComplete(42);
        assertEquals(42, delegate.completedCheckpoint);
    }

    @Test
    void rejectsAnUpdateSplitAcrossMailboxPollsBeforeCheckpoint() throws Exception {
        FakeSource delegate = new FakeSource((output, context) ->
                output.collect(new TableRecord(TABLE, RowKind.UPDATE_BEFORE, GenericRowData.of(1))));
        SourceReader<Event, TestSplit> reader =
                new TableEventSource<>(delegate, Map.of(TABLE, BEFORE)).createReader(context(null, new AtomicInteger()));
        assertThrows(IllegalStateException.class, () -> reader.pollNext(event -> {}));
        assertThrows(IllegalStateException.class, () -> reader.snapshotState(1));
    }

    @Test
    void emitsAcknowledgedSchemaBeforeRowsEvenWhenAcknowledgmentCompletesInsidePoll() throws Exception {
        AtomicInteger applied = new AtomicInteger();
        FakeSource delegate = new FakeSource((output, context) -> {
            context.sendSourceSchemaChange(change()).toCompletableFuture().join();
            output.collect(new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1, "next")));
        });
        SourceReader<Event, TestSplit> reader =
                new TableEventSource<>(delegate, Map.of(TABLE, BEFORE)).createReader(context(null, applied));
        List<Event> events = new ArrayList<>();
        reader.pollNext(events::add);
        assertEquals(3, events.size());
        assertInstanceOf(CreateTableEvent.class, events.get(0));
        assertInstanceOf(AddColumnEvent.class, events.get(1));
        assertInstanceOf(DataChangeEvent.class, events.get(2));
        assertEquals(1, applied.get());
    }

    @Test
    void recoveryReannouncesCommittedSchemaWithoutDuplicatingAReplayedDdl() throws Exception {
        AtomicInteger applied = new AtomicInteger();
        FakeSource delegate = new FakeSource((output, context) -> {
            context.sendSourceSchemaChange(change()).toCompletableFuture().join();
            output.collect(new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1, "restored")));
        });
        SourceReader<Event, TestSplit> reader =
                new TableEventSource<>(delegate, Map.of(TABLE, BEFORE)).createReader(context(AFTER, applied));
        List<Event> events = new ArrayList<>();
        reader.pollNext(events::add);
        assertEquals(2, events.size());
        assertEquals(AFTER, assertInstanceOf(CreateTableEvent.class, events.getFirst()).getPhysicalSchema());
        assertInstanceOf(DataChangeEvent.class, events.getLast());
        assertEquals(1, applied.get());
    }

    @Test
    void metadataDiscoveryNeverExpandsBeyondAuthorizedTables() {
        TableMetadataAccessor metadata = new TableMetadataAccessor(Map.of(TABLE, BEFORE));
        assertEquals(List.of("app"), metadata.listNamespaces());
        assertEquals(List.of(TABLE), metadata.listTables(null, null));
        assertEquals(List.of(), metadata.listTables("other", null));
        assertThrows(IllegalArgumentException.class,
                () -> metadata.getTableSchema(new TableId("secret", null, "orders")));
    }

    private static SourceSchemaChange change() {
        return new SourceSchemaChange("schema-event", TABLE, BEFORE, AFTER);
    }

    private static SourceReaderContext context(Schema restored, AtomicInteger applied) {
        return new SourceReaderContext() {
            @Override public Configuration getConfiguration() { return new Configuration(); }
            @Override public int getIndexOfSubtask() { return 0; }
            @Override public int currentParallelism() { return 1; }
            @Override public void sendSplitRequest() {}
            @Override public Optional<Schema> restoredSourceSchema(TableId table) { return Optional.ofNullable(restored); }
            @Override public CompletionStage<Long> sendSourceSchemaChange(SourceSchemaChange change) {
                applied.incrementAndGet();
                return CompletableFuture.completedFuture(1L);
            }
        };
    }

    @FunctionalInterface
    private interface Emission {
        void emit(ReaderOutput<TableRecord> output, SourceReaderContext context) throws Exception;
    }

    private static final class TestSplit implements SourceSplit {
        @Override public String splitId() { return "split"; }
    }

    private static final class FakeSource implements Source<TableRecord, TestSplit, String> {
        private final Emission emission;
        private final List<TestSplit> splits = List.of(new TestSplit());
        private long completedCheckpoint;
        private final SimpleVersionedSerializer<TestSplit> splitSerializer = new SimpleVersionedSerializer<>() {
            @Override public int getVersion() { return 3; }
            @Override public byte[] serialize(TestSplit split) { return new byte[] {3}; }
            @Override public TestSplit deserialize(int version, byte[] bytes) { return splits.getFirst(); }
        };
        private final SimpleVersionedSerializer<String> stateSerializer = new SimpleVersionedSerializer<>() {
            @Override public int getVersion() { return 5; }
            @Override public byte[] serialize(String state) { return state.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
            @Override public String deserialize(int version, byte[] bytes) { return new String(bytes, java.nio.charset.StandardCharsets.UTF_8); }
        };

        private FakeSource(Emission emission) { this.emission = emission; }
        @Override public Boundedness getBoundedness() { return Boundedness.CONTINUOUS_UNBOUNDED; }
        @Override public SplitEnumerator<TestSplit, String> createEnumerator(SplitEnumeratorContext<TestSplit> context) {
            throw new UnsupportedOperationException();
        }
        @Override public SplitEnumerator<TestSplit, String> restoreEnumerator(SplitEnumeratorContext<TestSplit> context, String state) {
            throw new UnsupportedOperationException();
        }
        @Override public SimpleVersionedSerializer<TestSplit> getSplitSerializer() { return splitSerializer; }
        @Override public SimpleVersionedSerializer<String> getEnumeratorCheckpointSerializer() { return stateSerializer; }
        @Override public SourceReader<TableRecord, TestSplit> createReader(SourceReaderContext context) {
            return new SourceReader<>() {
                @Override public void start() {}
                @Override public InputStatus pollNext(ReaderOutput<TableRecord> output) throws Exception {
                    emission.emit(output, context);
                    return InputStatus.NOTHING_AVAILABLE;
                }
                @Override public CompletableFuture<Void> isAvailable() { return CompletableFuture.completedFuture(null); }
                @Override public void addSplits(List<TestSplit> incoming) {}
                @Override public void notifyNoMoreSplits() {}
                @Override public List<TestSplit> snapshotState(long checkpointId) { return splits; }
                @Override public void notifyCheckpointComplete(long checkpointId) { completedCheckpoint = checkpointId; }
                @Override public void close() {}
            };
        }
    }
}
