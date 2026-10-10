package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Exercises Flink CDC-style in-chunk snapshot normalization without any live database. */
class MySqlChunkBackfillTest {

    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(100))),
            List.of("id"));

    @Test
    void normalizesRepeatedUpdatesInsertsAndDeletesWithinTheChunk() {
        MySqlChunkBackfill backfill = new MySqlChunkBackfill(split(null, 100L), SCHEMA, 5);
        backfill.addSnapshot(row(RowKind.INSERT, 1L, "initial"));
        backfill.addSnapshot(row(RowKind.INSERT, 2L, "removed"));
        backfill.apply(change(row(RowKind.UPDATE_BEFORE, 1L, "initial"),
                row(RowKind.UPDATE_AFTER, 1L, "intermediate")));
        backfill.apply(change(row(RowKind.UPDATE_BEFORE, 1L, "intermediate"),
                row(RowKind.UPDATE_AFTER, 1L, "final")));
        backfill.apply(change(row(RowKind.DELETE, 2L, "removed")));
        backfill.apply(change(row(RowKind.INSERT, 3L, "new")));

        assertEquals(Map.of(1L, "final", 3L, "new"), values(backfill.finish()));
        assertThrows(IllegalStateException.class, () -> backfill.addSnapshot(row(RowKind.INSERT, 4, "late")));
    }

    @Test
    void appliesPrimaryKeyMoveOnBothSidesOfTheBoundary() {
        MySqlChunkBackfill before = new MySqlChunkBackfill(split(null, 100L), SCHEMA, 5);
        MySqlChunkBackfill after = new MySqlChunkBackfill(split(100L, null), SCHEMA, 5);
        before.addSnapshot(row(RowKind.INSERT, 50L, "old"));
        BinlogEvent move = change(row(RowKind.UPDATE_BEFORE, 50L, "old"),
                row(RowKind.UPDATE_AFTER, 150L, "new"));

        before.apply(move);
        after.apply(move);
        assertEquals(Map.of(), values(before.finish()));
        assertEquals(Map.of(150L, "new"), values(after.finish()));
    }

    @Test
    void rejectsIncompleteUpdatePairsAndUnboundedSnapshotRows() {
        MySqlChunkBackfill backfill = new MySqlChunkBackfill(split(10L, 20L), SCHEMA, 1);
        assertThrows(IllegalArgumentException.class,
                () -> backfill.addSnapshot(row(RowKind.INSERT, 20L, "outside")));
        assertThrows(IllegalArgumentException.class,
                () -> backfill.apply(change(row(RowKind.UPDATE_BEFORE, 15L, "missing after"))));
        backfill.addSnapshot(row(RowKind.INSERT, 11L, "first"));
        assertThrows(IllegalStateException.class,
                () -> backfill.addSnapshot(row(RowKind.INSERT, 12L, "over capacity")));
    }

    private static MySqlSnapshotSplit split(Long lower, Long upper) {
        return new MySqlSnapshotSplit("chunk", "frozen", TABLE, lower, upper, null);
    }

    private static TableRecord row(RowKind kind, long id, String name) {
        return new TableRecord(TABLE, kind, GenericRowData.of(id, name));
    }

    private static BinlogEvent change(TableRecord... rows) {
        return new BinlogEvent(List.of(rows), new BinlogOffset(
                Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", 100L)));
    }

    private static Map<Long, String> values(List<TableRecord> records) {
        Map<Long, String> values = new LinkedHashMap<>();
        for (TableRecord record : records) {
            assertEquals(RowKind.INSERT, record.rowKind());
            values.put(record.row().getLong(0), record.row().getString(1));
        }
        return values;
    }
}
