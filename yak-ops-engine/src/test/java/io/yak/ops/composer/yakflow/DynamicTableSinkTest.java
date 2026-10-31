package io.yak.ops.composer.yakflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.sink.SchemaChangeAwareSinkWriter;
import io.yak.ops.core.api.connector.sink.SchemaEvolutionSink;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaVersionedTableState;
import io.yak.ops.core.sink.DataSink;
import io.yak.ops.core.sink.EventSinkProvider;
import io.yak.ops.core.types.LogicalTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DynamicTableSinkTest {
    private static final TableId FIRST = new TableId("app", null, "orders");
    private static final TableId SECOND = new TableId("app", null, "orders_2");
    private static final TableId OVERRIDE = new TableId("warehouse", null, "initial_override");
    private static final Schema SCHEMA = new Schema(
            List.of(new PhysicalColumn("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));

    @Test
    void newWriterAppearsOnlyAfterSchemaCommitAndCheckpointRestoresItsRoute() throws Exception {
        List<TableId> opened = new ArrayList<>();
        List<TableId> delivered = new ArrayList<>();
        AtomicInteger planned = new AtomicInteger();
        DynamicTableSink sink = sink(opened, delivered, planned);
        try (SinkWriter<Event> writer = sink.createWriter(null)) {
            assertEquals(List.of(OVERRIDE), opened);
            SourceSchemaChange change = new SourceSchemaChange("discovered-second", SECOND, null, SCHEMA);
            SchemaChangeRequest request = sink.planSchemaChange(change, false);
            assertSame(request, sink.planSchemaChange(change, false), "Identical reader acknowledgments share one admission");
            assertEquals(1, planned.get());
            assertTrue(request.newlyAdded());
            CreateTableEvent second = new CreateTableEvent(target(SECOND), SCHEMA);
            assertThrows(IllegalStateException.class, () -> writer.write(second, null));
            @SuppressWarnings("unchecked")
            SchemaChangeAwareSinkWriter<Event> schemas = (SchemaChangeAwareSinkWriter<Event>) writer;
            schemas.validateSchemaChange(request);
            schemas.onSchemaChange(request, 1);
            sink.schemaChangeCompleted(request);
            writer.write(second, null);
            assertEquals(List.of(OVERRIDE, target(SECOND)), opened);
            assertEquals(List.of(target(SECOND)), delivered);
        }

        List<TableId> reopened = new ArrayList<>();
        DynamicTableSink restored = sink(reopened, new ArrayList<>(), new AtomicInteger());
        restored.restoreSchemaState(List.of(state(FIRST, OVERRIDE, 0), state(SECOND, target(SECOND), 1)));
        try (SinkWriter<Event> ignored = restored.createWriter(null)) {
            assertEquals(List.of(OVERRIDE, target(SECOND)), reopened);
            assertEquals(OVERRIDE, restored.target(FIRST));
            assertEquals(target(SECOND), restored.target(SECOND));
        }
    }

    @Test
    void collidingOrChangedCheckpointRoutesFailBeforeOpeningANewWriter() throws Exception {
        FakeTable initial = new FakeTable(state(FIRST, OVERRIDE, 0), new ArrayList<>(), new ArrayList<>());
        AtomicInteger calls = new AtomicInteger();
        DynamicTableSink colliding = new DynamicTableSink(Map.of(FIRST, initial), source -> OVERRIDE,
                (source, raw, restored) -> {
                    calls.incrementAndGet();
                    throw new AssertionError("Naming collisions must be rejected before target metadata planning");
                });
        assertThrows(IllegalArgumentException.class,
                () -> colliding.planSchemaChange(new SourceSchemaChange("new", SECOND, null, SCHEMA), false));
        assertEquals(0, calls.get());

        DynamicTableSink restored = sink(new ArrayList<>(), new ArrayList<>(), new AtomicInteger());
        assertThrows(IllegalArgumentException.class,
                () -> restored.restoreSchemaState(List.of(state(FIRST, target(FIRST), 0))));
        assertThrows(IllegalArgumentException.class,
                () -> restored.restoreSchemaState(List.of()));
    }

    private static DynamicTableSink sink(List<TableId> opened, List<TableId> delivered, AtomicInteger planned) {
        return new DynamicTableSink(Map.of(FIRST, new FakeTable(state(FIRST, OVERRIDE, 0), opened, delivered)),
                DynamicTableSinkTest::target, (source, raw, restored) -> {
                    planned.incrementAndGet();
                    TableId target = target(source);
                    FakeTable table = new FakeTable(restored == null ? state(source, target, 0) : restored, opened, delivered);
                    SchemaChangeRequest request = restored == null
                            ? new SchemaChangeRequest("discovered-second", source, target, raw, raw, raw, raw,
                                    List.of(new CreateTableEvent(target, raw)), true) : null;
                    return new DynamicTableSink.Registration(table, request);
                });
    }

    private static TableId target(TableId source) {
        return new TableId("warehouse", null, "copy_" + source.table());
    }

    private static SchemaVersionedTableState state(TableId source, TableId target, long version) {
        return new SchemaVersionedTableState(source, target, SCHEMA, SCHEMA, version);
    }

    private static final class FakeTable implements DataSink, Sink<Event>, SchemaEvolutionSink {
        private SchemaVersionedTableState state;
        private final List<TableId> opened;
        private final List<TableId> delivered;

        private FakeTable(SchemaVersionedTableState state, List<TableId> opened, List<TableId> delivered) {
            this.state = state;
            this.opened = opened;
            this.delivered = delivered;
        }

        @Override public EventSinkProvider getEventSinkProvider() { return () -> this; }
        @Override public MetadataApplier getMetadataApplier() { return event -> {}; }
        @Override public MetadataApplier createMetadataApplier() { return getMetadataApplier(); }
        @Override public List<SchemaVersionedTableState> initialSchemaState() { return List.of(state); }
        @Override public void restoreSchemaState(List<SchemaVersionedTableState> states) { state = states.getFirst(); }
        @Override public SinkWriter<Event> createWriter(WriterInitContext context) {
            opened.add(state.targetTable());
            return new Writer();
        }

        private final class Writer implements SinkWriter<Event>, SchemaChangeAwareSinkWriter<Event> {
            @Override public void write(Event event, Context context) { delivered.add(((CreateTableEvent) event).tableId()); }
            @Override public void flush(boolean endOfInput) {}
            @Override public void close() {}
            @Override public void validateSchemaChange(SchemaChangeRequest request) {}
            @Override public void onSchemaChange(SchemaChangeRequest request, long version) {}
        }
    }
}
