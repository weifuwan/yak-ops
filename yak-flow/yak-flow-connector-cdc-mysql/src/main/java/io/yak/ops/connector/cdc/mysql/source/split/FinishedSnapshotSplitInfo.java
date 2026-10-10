package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.core.data.TableId;
import java.util.Objects;

/**
 * One normalized snapshot chunk's immutable Binlog filtering fence.
 *
 * <p>The High watermark is recorded only after bounded Backfill and normalized snapshot
 * emission. It must survive Checkpoint and be included in the final Binlog split so events
 * already reflected in an initial chunk are not replayed into the JDBC Sink.
 *
 * @param tableId original MySQL source table
 * @param splitId stable source split identifier
 * @param lowerInclusive nullable lower key boundary
 * @param upperExclusive nullable upper key boundary
 * @param highWatermark event boundary included in the normalized snapshot
 */
public record FinishedSnapshotSplitInfo(
        TableId tableId, String splitId, Long lowerInclusive, Long upperExclusive, BinlogOffset highWatermark) {

    public FinishedSnapshotSplitInfo {
        Objects.requireNonNull(tableId, "tableId");
        if (splitId == null || splitId.isBlank()) {
            throw new IllegalArgumentException("Finished chunk must have a stable split ID");
        }
        Objects.requireNonNull(highWatermark, "highWatermark");
        if (lowerInclusive != null && upperExclusive != null && lowerInclusive >= upperExclusive) {
            throw new IllegalArgumentException("Finished chunk boundaries are not increasing");
        }
    }

    /** Returns whether a BIGINT key belongs to this chunk's half-open range. */
    public boolean contains(long key) {
        return (lowerInclusive == null || key >= lowerInclusive) && (upperExclusive == null || key < upperExclusive);
    }
}
