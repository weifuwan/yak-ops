package io.yak.ops.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaUtils;
import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.LogicalTypes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaChangeEventsTest {

    private static final TableId TABLE = new TableId("db", null, "orders");
    private static final Schema INITIAL = new Schema(
            List.of(
                    new PhysicalColumn("id", LogicalTypes.BIGINT.copy(false)),
                    new PhysicalColumn("name", LogicalTypes.varchar(20))),
            List.of("id"),
            "hidden-table-comment");

    @Test
    void createEventPreservesLegacyWireStateAndPhysicalColumnAttributes() {
        Schema physical = new Schema(
                List.of(
                        new PhysicalColumn("id", LogicalTypes.BIGINT.copy(false)),
                        new PhysicalColumn(
                                "name",
                                LogicalTypes.varchar(20),
                                "hidden-column-comment",
                                new ColumnProperties("hidden-default", false, false, null, false))),
                List.of("id"),
                "hidden-table-comment");

        CreateTableEvent event = new CreateTableEvent(TABLE, physical);
        assertEquals(physical, event.getPhysicalSchema());
        assertEquals(physical.toTableSchema(), event.getSchema());
        assertEquals("hidden-table-comment", event.getTableComment());
        assertEquals("hidden-column-comment", event.getColumnComments().get("name"));
        assertEquals("hidden-default", event.getColumnProperties().get("name").defaultExpression());
        assertEquals(physical, event.copy(new TableId("next", null, "orders")).getPhysicalSchema());
        assertFalse(event.toString().contains("hidden-default"));
    }

    @Test
    void addsColumnsInStablePositionAndDoesNotMutatePreviousSchema() {
        AddColumnEvent add = new AddColumnEvent(
                TABLE,
                List.of(
                        AddColumnEvent.first(new PhysicalColumn("first", LogicalTypes.INTEGER)),
                        AddColumnEvent.after(new PhysicalColumn("middle", LogicalTypes.INTEGER), "id"),
                        AddColumnEvent.before(new PhysicalColumn("before_name", LogicalTypes.INTEGER), "name"),
                        AddColumnEvent.last(new PhysicalColumn("tail", LogicalTypes.INTEGER))));
        Schema evolved = SchemaUtils.applySchemaChangeEvent(INITIAL, add);
        assertEquals(
                List.of("first", "id", "middle", "before_name", "name", "tail"),
                evolved.columns().stream().map(PhysicalColumn::name).toList());
        assertEquals(List.of("id", "name"), INITIAL.columns().stream().map(PhysicalColumn::name).toList());
        assertEquals(INITIAL.primaryKeys(), evolved.primaryKeys());
        assertEquals(INITIAL.comment(), evolved.comment());
        assertEquals(SchemaChangeEventType.ADD_COLUMN, add.getType());
        assertEquals(add, add.copy(TABLE));
        assertNotEquals(add, add.copy(new TableId("other", null, "orders")));
    }

    @Test
    void rejectsDuplicateReferencesAndMissingTargetsBeforeMutatingSchema() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AddColumnEvent(TABLE, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> AddColumnEvent.after(new PhysicalColumn("x", LogicalTypes.INTEGER), ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaUtils.applySchemaChangeEvent(
                        INITIAL, new AddColumnEvent(TABLE, List.of(AddColumnEvent.last(INITIAL.columns().get(0))))));
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaUtils.applySchemaChangeEvent(
                        INITIAL,
                        new AddColumnEvent(
                                TABLE,
                                List.of(AddColumnEvent.before(
                                        new PhysicalColumn("new", LogicalTypes.INTEGER), "missing")))));
    }

    @Test
    void altersKnownTypesAndKeepsMetadataWithoutAllowingStalePreSchema() {
        Map<String, LogicalType> changes = new HashMap<>(Map.of("name", LogicalTypes.varchar(255)));
        AlterColumnTypeEvent alter = new AlterColumnTypeEvent(
                TABLE, changes, Map.of("name", LogicalTypes.varchar(20)));
        changes.clear();

        Schema evolved = SchemaUtils.applySchemaChangeEvent(INITIAL, alter);
        assertEquals(LogicalTypes.varchar(255), evolved.column("name").orElseThrow().type());
        assertEquals(LogicalTypes.varchar(20), INITIAL.column("name").orElseThrow().type());
        assertEquals(SchemaChangeEventType.ALTER_COLUMN_TYPE, alter.getType());
        assertEquals(alter, alter.copy(TABLE));
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaUtils.applySchemaChangeEvent(evolved, alter));
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaUtils.applySchemaChangeEvent(
                        INITIAL, new AlterColumnTypeEvent(TABLE, Map.of("absent", LogicalTypes.INTEGER))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AlterColumnTypeEvent(TABLE, Map.of()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> alter.typeMapping().clear());
    }

    @Test
    void dropColumnRequestValidatesManagedSourceAndTargetSchemaTransitions() {
        TableId target = new TableId("target", null, "orders");
        Schema after =
                new Schema(List.of(INITIAL.columns().getFirst()), INITIAL.primaryKeys(), INITIAL.comment());
        DropColumnEvent drop = new DropColumnEvent(
                target, List.of("name"), Map.of("name", LogicalTypes.varchar(20)));

        SchemaChangeRequest request = new SchemaChangeRequest(
                "binlog:0001:100", TABLE, target, INITIAL, after, INITIAL, after, List.of(drop));
        assertEquals(after, request.targetSchemaAfter());
        assertEquals(after, SchemaUtils.applySchemaChangeEvent(INITIAL, drop));
        assertEquals(SchemaChangeEventType.DROP_COLUMN, drop.getType());
        assertEquals(drop, drop.copy(target));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaChangeRequest(
                        "binlog:0001:101", TABLE, target, INITIAL, after, INITIAL, INITIAL, List.of(drop)));

        DropColumnEvent staleType = new DropColumnEvent(
                target, List.of("name"), Map.of("name", LogicalTypes.varchar(21)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaChangeRequest(
                        "binlog:0001:102", TABLE, target, INITIAL, after, INITIAL, after, List.of(staleType)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaUtils.applySchemaChangeEvent(INITIAL, staleType));

        Schema withoutKey = new Schema(List.of(INITIAL.columns().get(1)), List.of(), INITIAL.comment());
        DropColumnEvent primaryKey = new DropColumnEvent(
                target, List.of("id"), Map.of("id", LogicalTypes.BIGINT.copy(false)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaChangeRequest(
                        "binlog:0001:103",
                        TABLE,
                        target,
                        INITIAL,
                        withoutKey,
                        INITIAL,
                        withoutKey,
                        List.of(primaryKey)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SchemaUtils.applySchemaChangeEvent(INITIAL, primaryKey));
    }

    @Test
    void typeChangeCanInvalidateOldNativePropertiesWithoutChangingRawCheckpointMetadata() throws Exception {
        ColumnProperties nativeProperties = new ColumnProperties(null, false, false, null, false, "varchar(20)");
        Schema before = new Schema(List.of(
                INITIAL.columns().getFirst(),
                new PhysicalColumn("name", LogicalTypes.varchar(20), "name-comment", nativeProperties)),
                INITIAL.primaryKeys(), INITIAL.comment());
        Schema after = new Schema(List.of(
                INITIAL.columns().getFirst(),
                new PhysicalColumn("name", LogicalTypes.varchar(40), "name-comment", null)),
                INITIAL.primaryKeys(), INITIAL.comment());
        List<SchemaChangeEvent> events = SchemaUtils.diff(TABLE, before, after);
        assertEquals(List.of(new AlterColumnTypeEvent(TABLE,
                Map.of("name", LogicalTypes.varchar(40)), Map.of("name", LogicalTypes.varchar(20)))), events);
        assertEquals(after.toTableSchema(), SchemaUtils.applySchemaChangeEvent(before, events.getFirst()).toTableSchema());
        SourceSchemaChange raw = new SourceSchemaChange("ddl:100", TABLE, before, after);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(raw);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(raw, input.readObject());
        }
        assertEquals(nativeProperties, raw.before().column("name").orElseThrow().properties());
        assertEquals(null, raw.after().column("name").orElseThrow().properties());
    }

    @Test
    void diffRejectsUnchangedTypeMetadataLossAndNewCommentsDuringTypeChanges() {
        Schema before = new Schema(List.of(new PhysicalColumn("id", LogicalTypes.INTEGER,
                "comment", new ColumnProperties("1", false, false, null, false, "int"))), List.of());
        Schema lostDefault = new Schema(List.of(new PhysicalColumn("id", LogicalTypes.INTEGER, "comment", null)), List.of());
        Schema lostComment = new Schema(List.of(new PhysicalColumn("id", LogicalTypes.INTEGER,
                null, before.columns().getFirst().properties())), List.of());
        Schema changedCommentAndType = new Schema(List.of(new PhysicalColumn("id", LogicalTypes.BIGINT,
                "changed", null)), List.of());
        assertThrows(UnsupportedOperationException.class, () -> SchemaUtils.diff(TABLE, before, lostDefault));
        assertThrows(UnsupportedOperationException.class, () -> SchemaUtils.diff(TABLE, before, lostComment));
        assertThrows(UnsupportedOperationException.class, () -> SchemaUtils.diff(TABLE, before, changedCommentAndType));
    }

    @Test
    void oldAndNewSchemaEventsSerializeAndCopySafely() throws Exception {
        List<AddColumnEvent.ColumnWithPosition> columns = new ArrayList<>(
                List.of(AddColumnEvent.last(new PhysicalColumn("hidden", LogicalTypes.STRING, "private"))));
        AddColumnEvent add = new AddColumnEvent(TABLE, columns);
        columns.clear();

        SchemaChangeEvent[] events = {
            new CreateTableEvent(TABLE, INITIAL),
            add,
            new AlterColumnTypeEvent(TABLE, Map.of("name", LogicalTypes.varchar(40))),
            new DropColumnEvent(TABLE, List.of("name"), Map.of("name", LogicalTypes.varchar(20)))
        };
        for (SchemaChangeEvent event : events) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(event);
            }
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                assertEquals(event, input.readObject());
            }
            assertFalse(event.toString().contains("private"));
        }
        assertEquals(1, add.addedColumns().size());
        assertThrows(UnsupportedOperationException.class, () -> add.addedColumns().clear());
    }
}
