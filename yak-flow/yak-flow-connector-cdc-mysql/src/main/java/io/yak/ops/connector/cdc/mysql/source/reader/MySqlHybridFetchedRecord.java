package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableRecord;
import java.util.List;
import java.util.Objects;

/** One mailbox step: a prepared chunk, bounded output batch, complete Binlog change, or bootstrap anchor. */
public record MySqlHybridFetchedRecord(
        List<TableRecord> rows,
        Long snapshotKey,
        BinlogOffset binlogOffset,
        boolean lowWatermark,
        MySqlSnapshotSplit preparedSnapshot,
        Integer snapshotRowsEmitted) {

    public MySqlHybridFetchedRecord(
            List<TableRecord> rows, Long snapshotKey, BinlogOffset binlogOffset, boolean lowWatermark) {
        this(rows, snapshotKey, binlogOffset, lowWatermark, null, null);
    }

    public MySqlHybridFetchedRecord {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        int kinds = (snapshotKey == null ? 0 : 1)
                + (binlogOffset == null ? 0 : 1)
                + (preparedSnapshot == null ? 0 : 1)
                + (snapshotRowsEmitted == null ? 0 : 1);
        if (kinds != 1
                || (snapshotRowsEmitted != null && snapshotRowsEmitted < 0)
                || (preparedSnapshot != null && (preparedSnapshot.output() == null || !rows.isEmpty()))
                || (lowWatermark && (binlogOffset == null || !rows.isEmpty()))) {
            throw new IllegalArgumentException("Inconsistent Hybrid fetched record kind");
        }
    }

    public static MySqlHybridFetchedRecord prepared(MySqlSnapshotSplit split) {
        return new MySqlHybridFetchedRecord(List.of(), null, null, false, split, null);
    }

    public static MySqlHybridFetchedRecord snapshotRows(List<TableRecord> rows, int position) {
        return new MySqlHybridFetchedRecord(rows, null, null, false, null, position);
    }

    @Override
    public String toString() {
        return "MySqlHybridFetchedRecord{rowCount=" + rows.size() + ", values=redacted}";
    }
}
