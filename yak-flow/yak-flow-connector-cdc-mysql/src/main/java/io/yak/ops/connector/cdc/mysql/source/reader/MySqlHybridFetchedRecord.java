package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.core.data.TableRecord;
import java.util.List;
import java.util.Objects;

/**
 * One mailbox step: snapshot row, Binlog change, or a schema-history-ready low watermark.
 *
 * <p>Both halves of a Binlog UPDATE stay in the same rows list.
 */
public record MySqlHybridFetchedRecord(
        List<TableRecord> rows, Long snapshotKey, BinlogOffset binlogOffset, boolean lowWatermark) {

    public MySqlHybridFetchedRecord {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (snapshotKey == null && binlogOffset == null) {
            throw new IllegalArgumentException("Hybrid fetched record lacks a progress cursor");
        }
        if (snapshotKey != null && binlogOffset != null) {
            throw new IllegalArgumentException("Snapshot row and Binlog offset must be separate");
        }
        if (lowWatermark && (binlogOffset == null || !rows.isEmpty())) {
            throw new IllegalArgumentException("Low watermark is a metadata event without rows");
        }
    }
}
