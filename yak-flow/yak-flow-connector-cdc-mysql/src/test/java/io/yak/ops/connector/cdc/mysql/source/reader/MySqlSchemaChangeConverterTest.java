package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.configuration.SchemaEvolutionOptions;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.Test;

/** Exercises structured Debezium DDL, ordered row conversion and Source mailbox gating. */
class MySqlSchemaChangeConverterTest {

    private static final TableId SOURCE = new TableId("shop", null, "orders");
    private static final TableSchema ORIGINAL = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(64))),
            List.of("id"));

    private static final org.apache.kafka.connect.data.Schema TABLE_CHANGE =
            SchemaBuilder.struct()
                    .field("type", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                    .field("id", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                    .field("table", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                    .build();

    private static final org.apache.kafka.connect.data.Schema DDL =
            SchemaBuilder.struct()
                    .field("ddl", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                    .field("tableChanges", SchemaBuilder.array(TABLE_CHANGE).build())
                    .build();

    private static final String NEW_TABLE = """
            {"primaryKeyColumnNames":["id"],"columns":[
                {"name":"id","jdbcType":-5,"typeName":"BIGINT","optional":false},
                {"name":"name","jdbcType":12,"typeName":"VARCHAR","length":64,"optional":true},
                {"name":"note","jdbcType":12,"typeName":"VARCHAR","length":128,"optional":true}
            ]}
            """;

    @Test
    void ddlCarriesSchemaChangeAndFollowingRowsUseNewColumnPositions() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        BinlogEvent change = converter.convert(ddl("ALTER", NEW_TABLE, 100L));
        assertTrue(change.records().isEmpty());
        SourceSchemaChange schema = change.schemaChange();
        assertEquals(SOURCE, schema.tableId());
        assertEquals(2, schema.before().columns().size());
        assertEquals(3, schema.after().columns().size());
        assertEquals("note", schema.after().columns().getLast().name());
        assertEquals(128, schema.after().toTableSchema().column(2).length());

        BinlogEvent row = converter.convert(insertWithNote(101L));
        assertEquals(1, row.records().size());
        assertEquals(3, row.records().getFirst().row().getArity());
        assertEquals("hello", row.records().getFirst().row().getString(2));
        assertTrue(schema.eventId().length() == 64);
    }

    @Test
    void emptyStructuredTableChangesAdvancesOffsetWithoutMutatingRegisteredSchema() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        Struct ddl = new Struct(DDL)
                .put("ddl", "CREATE DATABASE unrelated")
                .put("tableChanges", List.of());
        SourceRecord source = new SourceRecord(
                Map.of("server", "yak"),
                Map.of("file", "mysql-bin.000001", "pos", 99L),
                "yak.shop",
                DDL,
                ddl);
        BinlogEvent ignored = converter.convert(source);
        assertTrue(ignored.records().isEmpty());
        assertEquals(null, ignored.schemaChange());
        assertEquals(99L, ignored.offset().position().get("pos"));

        SourceSchemaChange later = converter.convert(ddl("ALTER", NEW_TABLE, 100L)).schemaChange();
        assertEquals(2, later.before().columns().size());
        assertEquals(3, later.after().columns().size());
    }

    @Test
    void rowSchemaMismatchReconcilesBeforeTheRowWithoutAdvancingItsOffset() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        SourceRecord row = insertWithNote(101L);
        assertEquals(SOURCE, converter.schemaMismatch(row));

        TableSchema evolved = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(64)),
                        new Column("note", LogicalTypes.varchar(128))),
                List.of("id"));
        var previous = new io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        var current = new io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset(
                new java.util.LinkedHashMap<String, Object>(row.sourcePartition()),
                new java.util.LinkedHashMap<String, Object>(row.sourceOffset()));
        BinlogEvent boundary = converter.reconcile(SOURCE, evolved, previous, current);
        assertEquals(100L, boundary.offset().position().get("pos"));
        assertTrue(boundary.records().isEmpty());
        assertEquals(3, boundary.schemaChange().after().columns().size());
        assertEquals(null, converter.schemaMismatch(row));

        BinlogEvent delivered = converter.convert(row);
        assertEquals(101L, delivered.offset().position().get("pos"));
        assertEquals(3, delivered.records().getFirst().row().getArity());
        assertEquals("hello", delivered.records().getFirst().row().getString(2));
    }

    @Test
    void disablingEvolutionRejectsDdlBeforeAdvancingTheOffset() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL));
        assertThrows(UnsupportedOperationException.class, () -> converter.convert(ddl("ALTER", NEW_TABLE, 100L)));
    }

    @Test
    void rejectsDropRenameAndKeyMutation() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(SOURCE, ORIGINAL), Map.of(), true);
        assertThrows(UnsupportedOperationException.class, () -> converter.convert(ddl("DROP", NEW_TABLE, 100L)));
        String changedKey = NEW_TABLE.replace("[\"id\"]", "[\"name\"]");
        assertThrows(UnsupportedOperationException.class, () -> converter.convert(ddl("ALTER", changedKey, 101L)));
    }

    @Test
    void pendingDdlCannotBeCheckpointedAndReleasesRowsOnlyAfterAcknowledgment() {
        CompletableFuture<Long> acknowledgment = new CompletableFuture<>();
        AtomicBoolean progressed = new AtomicBoolean();
        SourceReaderContext context = new SourceReaderContext() {
            @Override
            public Configuration getConfiguration() {
                return new Configuration();
            }

            @Override
            public int getIndexOfSubtask() {
                return 0;
            }

            @Override
            public int currentParallelism() {
                return 1;
            }

            @Override
            public void sendSplitRequest() {}

            @Override
            public CompletionStage<Long> sendSourceSchemaChange(SourceSchemaChange change) {
                return acknowledgment;
            }
        };
        var gate = new MySqlSchemaChangeGate(context);
        Schema before = Schema.from(ORIGINAL, null, Map.of(), Map.of());
        Schema after = new Schema(
                List.of(
                        before.columns().get(0),
                        before.columns().get(1),
                        new io.yak.ops.core.schema.PhysicalColumn("note", LogicalTypes.varchar(128))),
                List.of("id"));
        gate.begin(new SourceSchemaChange("position-100", SOURCE, before, after), () -> progressed.set(true));
        assertFalse(gate.consumeAcknowledgment());
        assertThrows(IllegalStateException.class, gate::requireCheckpointReady);
        acknowledgment.complete(1L);
        assertTrue(gate.consumeAcknowledgment());
        assertTrue(progressed.get());
        gate.requireCheckpointReady();
    }

    private static SourceRecord ddl(String type, String tableJson, long position) {
        Struct tableChange =
                new Struct(TABLE_CHANGE).put("type", type).put("id", "shop.orders").put("table", tableJson);
        Struct ddl = new Struct(DDL)
                .put("ddl", "redacted-structured-source-event")
                .put("tableChanges", List.of(tableChange));
        return new SourceRecord(
                Map.of("server", "yak"),
                Map.of("file", "mysql-bin.000001", "pos", position),
                "yak.shop",
                DDL,
                ddl);
    }

    private static SourceRecord insertWithNote(long position) {
        org.apache.kafka.connect.data.Schema rowSchema = SchemaBuilder.struct()
                .field("id", org.apache.kafka.connect.data.Schema.INT64_SCHEMA)
                .field("name", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("note", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .build();
        org.apache.kafka.connect.data.Schema sourceSchema = SchemaBuilder.struct()
                .field("db", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("table", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .build();
        org.apache.kafka.connect.data.Schema envelopeSchema = SchemaBuilder.struct()
                .field("op", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("source", sourceSchema)
                .field("after", rowSchema)
                .build();
        Struct source = new Struct(sourceSchema).put("db", "shop").put("table", "orders");
        Struct row = new Struct(rowSchema).put("id", 1L).put("name", "order").put("note", "hello");
        Struct envelope = new Struct(envelopeSchema).put("op", "c").put("source", source).put("after", row);
        return new SourceRecord(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", position),
                "yak.shop.orders", envelopeSchema, envelope);
    }
}
