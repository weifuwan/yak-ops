package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies per-chunk filtering and atomic primary-key move handling. */
class MySqlBinlogSplitFilterTest {

    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(100))),
            List.of("id"));

    @Test
    void dropsOverlapsOnlyWhenAnEventIsWithinItsOwnChunkHighWatermark() {
        MySqlBinlogSplitFilter filter = filter();
        assertEquals(List.of(), filter.filter(event(150, row(RowKind.INSERT, 1, "before high"))).records());
        assertEquals(List.of(RowKind.INSERT), filter.filter(event(250, row(RowKind.INSERT, 1, "after high")))
                .records().stream().map(TableRecord::rowKind).toList());

        // The second chunk reached a later watermark; the same event is already normalized there.
        assertEquals(List.of(), filter.filter(event(250, row(RowKind.INSERT, 101, "within high"))).records());
        assertEquals(List.of(RowKind.DELETE), filter.filter(event(350, row(RowKind.DELETE, 101, "after high")))
                .records().stream().map(TableRecord::rowKind).toList());
    }

    @Test
    void projectsHalfOfCrossChunkKeyMoveIntoStandaloneChangelog() {
        MySqlBinlogSplitFilter filter = filter();
        BinlogEvent move = event(250, row(RowKind.UPDATE_BEFORE, 1, "old"),
                row(RowKind.UPDATE_AFTER, 101, "new"));
        BinlogEvent filtered = filter.filter(move);
        assertEquals(List.of(RowKind.DELETE), filtered.records().stream().map(TableRecord::rowKind).toList());
        assertEquals(1L, filtered.records().getFirst().row().getLong(0));

        BinlogEvent inverse = event(250, row(RowKind.UPDATE_BEFORE, 101, "old"),
                row(RowKind.UPDATE_AFTER, 1, "new"));
        BinlogEvent other = filter.filter(inverse);
        assertEquals(List.of(RowKind.INSERT), other.records().stream().map(TableRecord::rowKind).toList());
        assertEquals(1L, other.records().getFirst().row().getLong(0));

        assertEquals(List.of(), filter.filter(event(150, row(RowKind.UPDATE_BEFORE, 1, "old"),
                row(RowKind.UPDATE_AFTER, 2, "new"))).records());
    }

    @Test
    void refusesUnknownTableUncoveredKeyAndIncompleteUpdate() {
        MySqlBinlogSplitFilter filter = filter();
        assertThrows(IllegalArgumentException.class,
                () -> filter.filter(event(350, row(RowKind.UPDATE_BEFORE, 1, "old"))));
        assertThrows(IllegalArgumentException.class, () -> filter.filter(new BinlogEvent(
                List.of(new TableRecord(
                        new TableId("other", null, "orders"), RowKind.INSERT, GenericRowData.of(1L, "no"))),
                offset(350))));
    }

    private static MySqlBinlogSplitFilter filter() {
        return new MySqlBinlogSplitFilter(
                Map.of(TABLE, SCHEMA),
                List.of(
                        new FinishedSnapshotSplitInfo(TABLE, "first", null, 100L, offset(200)),
                        new FinishedSnapshotSplitInfo(TABLE, "second", 100L, null, offset(300))));
    }

    private static TableRecord row(RowKind kind, long id, String name) {
        return new TableRecord(TABLE, kind, GenericRowData.of(id, name));
    }

    private static BinlogEvent event(long position, TableRecord... records) {
        return new BinlogEvent(List.of(records), offset(position));
    }

    private static BinlogOffset offset(long position) {
        return new BinlogOffset(Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
