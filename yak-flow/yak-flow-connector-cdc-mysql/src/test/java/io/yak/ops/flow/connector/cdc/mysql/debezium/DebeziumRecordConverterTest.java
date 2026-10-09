package io.yak.ops.flow.connector.cdc.mysql.debezium;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.flow.api.row.RowKind;
import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTypes;
import io.yak.ops.flow.api.row.YakTableSchema;
import java.util.List;
import java.util.Map;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.Test;

class DebeziumRecordConverterTest {

    private static final YakTableSchema YAK_SCHEMA = new YakTableSchema(
            List.of(
                    new YakColumn("id", YakTypes.BIGINT, false, null),
                    new YakColumn("name", YakTypes.STRING, true, 100)),
            List.of("id"));
    private static final Schema ROW_SCHEMA = SchemaBuilder.struct()
            .name("test.inventory.Value")
            .optional()
            .field("id", Schema.INT64_SCHEMA)
            .field("name", Schema.OPTIONAL_STRING_SCHEMA)
            .build();
    private static final Schema ENVELOPE_SCHEMA = SchemaBuilder.struct()
            .name("test.inventory.Envelope")
            .field("before", ROW_SCHEMA)
            .field("after", ROW_SCHEMA)
            .field("op", Schema.STRING_SCHEMA)
            .build();

    private final DebeziumRecordConverter converter = new DebeziumRecordConverter(YAK_SCHEMA);

    @Test
    void shouldConvertSnapshotAndCreateToInsert() {
        assertEquals(
                List.of(RowKind.INSERT),
                converter.convert(record("r", null, row(1L, "snapshot"))).stream()
                        .map(value -> value.rowKind())
                        .toList());
        assertEquals(
                List.of(RowKind.INSERT),
                converter.convert(record("c", null, row(2L, "created"))).stream()
                        .map(value -> value.rowKind())
                        .toList());
    }

    @Test
    void shouldConvertUpdateToBeforeAndAfter() {
        var rows = converter.convert(record("u", row(1L, "before"), row(1L, "after")));

        assertEquals(List.of(RowKind.UPDATE_BEFORE, RowKind.UPDATE_AFTER), rows.stream()
                .map(value -> value.rowKind())
                .toList());
        assertEquals(List.of(1L, "before"), rows.get(0).values());
        assertEquals(List.of(1L, "after"), rows.get(1).values());
    }

    @Test
    void shouldConvertDeleteToDelete() {
        var rows = converter.convert(record("d", row(3L, "deleted"), null));

        assertEquals(1, rows.size());
        assertEquals(RowKind.DELETE, rows.get(0).rowKind());
        assertEquals(List.of(3L, "deleted"), rows.get(0).values());
    }

    private Struct row(long id, String name) {
        return new Struct(ROW_SCHEMA).put("id", id).put("name", name);
    }

    private SourceRecord record(String operation, Struct before, Struct after) {
        Struct envelope =
                new Struct(ENVELOPE_SCHEMA).put("before", before).put("after", after).put("op", operation);
        return new SourceRecord(
                Map.of("server", "test"),
                Map.of("file", "binlog.000001", "pos", 4L),
                "test.inventory",
                null,
                null,
                null,
                ENVELOPE_SCHEMA,
                envelope);
    }
}
