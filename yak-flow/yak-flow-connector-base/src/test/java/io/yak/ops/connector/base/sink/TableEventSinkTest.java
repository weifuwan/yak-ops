package io.yak.ops.connector.base.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.core.api.connector.sink.CancellableSinkWriter;
import io.yak.ops.core.api.connector.sink.Committer;
import io.yak.ops.core.api.connector.sink.CommittingSinkWriter;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.StatefulSinkWriter;
import io.yak.ops.core.api.connector.sink.SupportsCommitter;
import io.yak.ops.core.api.connector.sink.SupportsWriterState;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TableEventSinkTest {
    private static final TableId SOURCE = new TableId("source", null, "orders");
    private static final TableId TARGET = new TableId("target", null, "copied_orders");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(new Column("id", LogicalTypes.INTEGER.copy(false))), List.of("id"));

    @Test
    void restoresOriginalSourceIdentityAndAtomicImageSequenceForTheWriter() throws Exception {
        List<TableRecord> records = new ArrayList<>();
        Sink<TableRecord> delegate = context -> new PlainWriter(records);
        Sink<Event> sink = TableEventSink.of(delegate, tables());
        assertFalse(sink instanceof SupportsCommitter<?, ?>);
        assertFalse(sink instanceof SupportsWriterState<?, ?>);
        SinkWriter<Event> writer = sink.createWriter(null);
        assertFalse(writer instanceof CommittingSinkWriter<?, ?>);
        assertFalse(writer instanceof CancellableSinkWriter<?>);
        assertThrows(IllegalStateException.class, () ->
                writer.write(DataChangeEvent.insertEvent(TARGET, GenericRowData.of(1)), null));
        writer.write(new CreateTableEvent(TARGET, SCHEMA), null);
        ChangeEventId identity = new ChangeEventId("cdc-source", "binlog:1024", 0, 2);
        writer.write(DataChangeEvent.updateEvent(TARGET, GenericRowData.of(1), GenericRowData.of(2))
                .withChangeEventId(identity), null);
        assertEquals(2, records.size());
        assertEquals(SOURCE, records.getFirst().tableId());
        assertEquals(SOURCE, records.getLast().tableId());
        assertEquals(RowKind.UPDATE_BEFORE, records.getFirst().rowKind());
        assertEquals(RowKind.UPDATE_AFTER, records.getLast().rowKind());
        assertEquals(identity, records.getFirst().changeEventId());
        assertEquals(new ChangeEventId("cdc-source", "binlog:1024", 1, 2), records.getLast().changeEventId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesStateAndCommitSerializersAndForwardsRecoveryWithoutPrematureCommit() throws Exception {
        StatefulCommittingSink delegate = new StatefulCommittingSink();
        Sink<Event> sink = TableEventSink.of(delegate, tables());
        SupportsCommitter<Event, String> committing =
                (SupportsCommitter<Event, String>) assertInstanceOf(SupportsCommitter.class, sink);
        SupportsWriterState<Event, String> stateful =
                (SupportsWriterState<Event, String>) assertInstanceOf(SupportsWriterState.class, sink);
        assertSame(delegate.serializer, committing.getCommittableSerializer());
        assertSame(delegate.serializer, stateful.getWriterStateSerializer());
        StatefulSinkWriter<Event, String> writer = stateful.restoreWriter(null, List.of("recovered-writer-state"));
        assertEquals(List.of("recovered-writer-state"), delegate.restored);
        assertEquals(List.of("state:17"), writer.snapshotState(17));
        writer.flush(false);
        assertEquals(List.of(false), delegate.writer.flushes);
        Collection<String> transactions =
                ((CommittingSinkWriter<Event, String>) writer).prepareCommit(17);
        assertEquals(List.of("transaction:17"), transactions);
        assertTrue(delegate.committed.isEmpty());
        committing.createCommitter(null).commit(transactions, true);
        assertEquals(List.of("transaction:17"), delegate.committed);
        assertTrue(delegate.recoveredCommit);
        ((CancellableSinkWriter<?>) writer).cancel();
        assertTrue(delegate.writer.cancelled);
        assertEquals(1, delegate.writer.flushes.size());
    }

    private static List<TableSinkFactoryContext> tables() {
        return List.of(new TableSinkFactoryContext(ReadonlyConfig.fromMap(Map.of()), SOURCE, SCHEMA, TARGET, SCHEMA));
    }

    private static class PlainWriter implements SinkWriter<TableRecord> {
        private final List<TableRecord> records;
        private PlainWriter(List<TableRecord> records) { this.records = records; }
        @Override public void write(TableRecord record, Context context) { records.add(record); }
        @Override public void flush(boolean endOfInput) {}
        @Override public void close() {}
    }

    private static final class StatefulCommittingWriter implements StatefulSinkWriter<TableRecord, String>,
            CommittingSinkWriter<TableRecord, String>, CancellableSinkWriter<TableRecord> {
        private final List<Boolean> flushes = new ArrayList<>();
        private boolean cancelled;
        @Override public void write(TableRecord record, Context context) {}
        @Override public void flush(boolean endOfInput) { flushes.add(endOfInput); }
        @Override public void close() {}
        @Override public List<String> snapshotState(long checkpointId) { return List.of("state:" + checkpointId); }
        @Override public Collection<String> prepareCommit(long checkpointId) { return List.of("transaction:" + checkpointId); }
        @Override public void cancel() { cancelled = true; }
    }

    private static final class StatefulCommittingSink implements SupportsCommitter<TableRecord, String>,
            SupportsWriterState<TableRecord, String> {
        private final StatefulCommittingWriter writer = new StatefulCommittingWriter();
        private Collection<String> restored = List.of();
        private final List<String> committed = new ArrayList<>();
        private boolean recoveredCommit;
        private final SimpleVersionedSerializer<String> serializer = new SimpleVersionedSerializer<>() {
            @Override public int getVersion() { return 11; }
            @Override public byte[] serialize(String value) { return value.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
            @Override public String deserialize(int version, byte[] bytes) { return new String(bytes, java.nio.charset.StandardCharsets.UTF_8); }
        };
        @Override public SinkWriter<TableRecord> createWriter(WriterInitContext context) { return writer; }
        @Override public StatefulSinkWriter<TableRecord, String> restoreWriter(WriterInitContext context, Collection<String> state) {
            restored = List.copyOf(state);
            return writer;
        }
        @Override public SimpleVersionedSerializer<String> getWriterStateSerializer() { return serializer; }
        @Override public SimpleVersionedSerializer<String> getCommittableSerializer() { return serializer; }
        @Override public Committer<String> createCommitter(WriterInitContext context) {
            return new Committer<>() {
                @Override public void commit(Collection<String> values, boolean recovered) {
                    committed.addAll(values);
                    recoveredCommit = recovered;
                }
                @Override public void close() {}
            };
        }
    }
}
