package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.Test;

/** Verifies atomic UPDATE emission and checkpoint progress only after downstream delivery. */
class MySqlDebeziumRecordConverterTest {

    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final TableSchema YAK_SCHEMA = new TableSchema(
            List.of(
                    new Column("ID", LogicalTypes.BIGINT.copy(false)),
                    new Column("NAME", LogicalTypes.varchar(100))),
            List.of("ID"));
    private static final Schema CONNECT_ROW = SchemaBuilder.struct()
            .optional()
            .field("ID", Schema.INT64_SCHEMA)
            .field("NAME", Schema.STRING_SCHEMA)
            .build();
    private static final Schema CONNECT_SOURCE = SchemaBuilder.struct()
            .field("db", Schema.STRING_SCHEMA)
            .field("table", Schema.STRING_SCHEMA)
            .build();
    private static final Schema CONNECT_ENVELOPE = SchemaBuilder.struct()
            .field("op", Schema.STRING_SCHEMA)
            .field("source", CONNECT_SOURCE)
            .field("before", CONNECT_ROW)
            .field("after", CONNECT_ROW)
            .build();

    @Test
    void updateProducesAdjacentBeforeAfterAndCheckpointMovesAfterBoth() throws Exception {
        var converter = new MySqlDebeziumRecordConverter(Map.of(TABLE, YAK_SCHEMA));
        BinlogEvent event = converter.convert(record("u", row(1L, "old"), row(1L, "new")));
        assertEquals(List.of(RowKind.UPDATE_BEFORE, RowKind.UPDATE_AFTER),
                event.records().stream().map(TableRecord::rowKind).toList());
        assertEquals("new", event.records().getLast().row().getString(1));

        var state = new MySqlCdcSplitState(new io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit(
                "fingerprint", null, new byte[0]));
        var delivered = new ArrayList<TableRecord>();
        var emitter = new MySqlCdcRecordEmitter();
        assertThrows(IllegalStateException.class, () -> state.checkpoint(new byte[] {1}));
        emitter.emitRecord(event, delivered::add, state);
        assertEquals(2, delivered.size());
        assertEquals(event.offset(), state.checkpoint(new byte[] {1}).offset());

        var failedState = new MySqlCdcSplitState(new io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit(
                "fingerprint", null, new byte[0]));
        assertThrows(IllegalStateException.class,
                () -> emitter.emitRecord(event, row -> {
                    if (row.rowKind() == RowKind.UPDATE_AFTER) {
                        throw new IllegalStateException("downstream failed");
                    }
                }, failedState));
        assertThrows(IllegalStateException.class, () -> failedState.checkpoint(new byte[] {1}));
    }

    @Test
    void ignoresTombstoneButRejectsMissingUpdateBeforeImage() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(TABLE, YAK_SCHEMA));
        BinlogEvent tombstone = converter.convert(new SourceRecord(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 12L),
                "yak.shop.orders", null, null));
        assertEquals(0, tombstone.records().size());
        assertThrows(IllegalArgumentException.class, () -> converter.convert(record("u", null, row(1L, "new"))));
        assertEquals(RowKind.DELETE, converter.convert(record("d", row(1L, "gone"), null)).records().getFirst().rowKind());
    }


    @Test
    void detectsSameNameVarcharWideningBeforeRowEmission() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(TABLE, YAK_SCHEMA), Map.of(), true);
        Struct unchanged = rowWithCapacity(100, "normal");
        Struct widened = rowWithCapacity(180, "a-name-longer-than-thirty-two-characters");
        assertTrue(converter.matchesRowSchema(unchanged, YAK_SCHEMA));
        assertNull(converter.schemaMismatch(recordWithRow(unchanged)));
        assertEquals(TABLE, converter.schemaMismatch(recordWithRow(widened)));
        // Reconciliation must reject live metadata that is ahead of the
        // actual row or does not yet describe its type.
        assertFalse(converter.matchesRowSchema(widened, YAK_SCHEMA));
        var widenedSchema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                        new Column("NAME", LogicalTypes.varchar(180))),
                List.of("ID"));
        assertTrue(converter.matchesRowSchema(widened, widenedSchema));
    }

    @Test
    void withoutPropagatedMetadataFallsBackToColumnNames() {
        var converter = new MySqlDebeziumRecordConverter(Map.of(TABLE, YAK_SCHEMA), Map.of(), true);
        assertNull(converter.schemaMismatch(recordWithRow(row(1L, "normal"))));
    }

    private static Struct rowWithCapacity(int length, String name) {
        Schema rowSchema = SchemaBuilder.struct()
                .optional()
                .field("ID", Schema.INT64_SCHEMA)
                .field("NAME", SchemaBuilder.string()
                        .parameter("__debezium.source.column.length", Integer.toString(length))
                        .build())
                .build();
        return new Struct(rowSchema).put("ID", 1L).put("NAME", name);
    }

    private static SourceRecord recordWithRow(Struct row) {
        Schema envelopeSchema = SchemaBuilder.struct()
                .field("op", Schema.STRING_SCHEMA)
                .field("source", CONNECT_SOURCE)
                .field("before", row.schema())
                .field("after", row.schema())
                .build();
        Struct origin = new Struct(CONNECT_SOURCE).put("db", "shop").put("table", "orders");
        Struct envelope = new Struct(envelopeSchema)
                .put("op", "u")
                .put("source", origin)
                .put("before", row)
                .put("after", row);
        return new SourceRecord(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 30L),
                "yak.shop.orders", envelopeSchema, envelope);
    }

    private static Struct row(long id, String name) {
        return new Struct(CONNECT_ROW).put("ID", id).put("NAME", name);
    }

    private static SourceRecord record(String operation, Struct before, Struct after) {
        Struct origin = new Struct(CONNECT_SOURCE).put("db", "shop").put("table", "orders");
        Struct envelope = new Struct(CONNECT_ENVELOPE)
                .put("op", operation)
                .put("source", origin)
                .put("before", before)
                .put("after", after);
        return new SourceRecord(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 30L),
                "yak.shop.orders", CONNECT_ENVELOPE, envelope);
    }
}
