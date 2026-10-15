package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.core.data.TableId;
import java.util.Objects;

/** Frozen key range, durable normalized output, and mailbox-owned output position for one chunk. */
public record MySqlSnapshotSplit(
        String splitId,
        String fingerprint,
        TableId tableId,
        Long lowerInclusive,
        Long upperExclusive,
        Long lastEmittedKey,
        MySqlSnapshotOutput output,
        int outputPosition,
        FinishedSnapshotSplitInfo finished)
        implements MySqlHybridSplit {

    /** Creates an unprepared range; numeric cursors are used only by the JDBC scan primitive. */
    public MySqlSnapshotSplit(
            String splitId,
            String fingerprint,
            TableId tableId,
            Long lowerInclusive,
            Long upperExclusive,
            Long lastEmittedKey) {
        this(splitId, fingerprint, tableId, lowerInclusive, upperExclusive, lastEmittedKey, null, 0, null);
    }

    public MySqlSnapshotSplit {
        if (splitId == null || splitId.isBlank() || fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("Snapshot split requires a stable ID and fingerprint");
        }
        Objects.requireNonNull(tableId, "tableId");
        if (lowerInclusive != null && upperExclusive != null && lowerInclusive >= upperExclusive) {
            throw new IllegalArgumentException("Snapshot split bounds must be strictly increasing");
        }
        if (lastEmittedKey != null
                && ((lowerInclusive != null && lastEmittedKey < lowerInclusive)
                        || (upperExclusive != null && lastEmittedKey >= upperExclusive))) {
            throw new IllegalArgumentException("Snapshot split cursor is outside its range");
        }
        if (outputPosition < 0
                || (output == null && outputPosition != 0)
                || (output != null
                        && (finished != null
                                || lastEmittedKey != null
                                || outputPosition > output.rows().size()))) {
            throw new IllegalArgumentException("Inconsistent normalized snapshot output state");
        }
        if (output != null && output.rows().stream().anyMatch(row -> !tableId.equals(row.tableId()))) {
            throw new IllegalArgumentException("Snapshot output crosses source tables");
        }
        if (finished != null
                && (!splitId.equals(finished.splitId())
                        || !tableId.equals(finished.tableId())
                        || !Objects.equals(lowerInclusive, finished.lowerInclusive())
                        || !Objects.equals(upperExclusive, finished.upperExclusive())
                        || lastEmittedKey != null)) {
            throw new IllegalArgumentException("Finished snapshot metadata differs from assigned range");
        }
    }

    public boolean prepared() {
        return output != null || finished != null;
    }

    public MySqlSnapshotSplit withOutput(MySqlSnapshotOutput normalized) {
        if (prepared() || lastEmittedKey != null)
            throw new IllegalStateException("Snapshot is already prepared or advanced");
        return new MySqlSnapshotSplit(
                splitId,
                fingerprint,
                tableId,
                lowerInclusive,
                upperExclusive,
                null,
                Objects.requireNonNull(normalized, "normalized"),
                0,
                null);
    }

    /** Advances only after all rows in the emitted batch were accepted by the downstream collector. */
    public MySqlSnapshotSplit withOutputPosition(int position) {
        if (output == null
                || position < outputPosition
                || position > output.rows().size()
                || (position == outputPosition && !output.rows().isEmpty())) {
            throw new IllegalArgumentException("Invalid snapshot output progress");
        }
        if (position == output.rows().size()) {
            return new MySqlSnapshotSplit(
                    splitId,
                    fingerprint,
                    tableId,
                    lowerInclusive,
                    upperExclusive,
                    null,
                    null,
                    0,
                    new FinishedSnapshotSplitInfo(tableId, splitId, lowerInclusive, upperExclusive, output.high()));
        }
        return new MySqlSnapshotSplit(
                splitId, fingerprint, tableId, lowerInclusive, upperExclusive, null, output, position, null);
    }

    public MySqlSnapshotSplit withLastEmittedKey(long key) {
        if (prepared() || (lastEmittedKey != null && key <= lastEmittedKey)) {
            throw new IllegalArgumentException("Snapshot key must advance before normalization only");
        }
        return new MySqlSnapshotSplit(splitId, fingerprint, tableId, lowerInclusive, upperExclusive, key);
    }

    @Override
    public String toString() {
        return "MySqlSnapshotSplit{id=" + splitId + ", prepared=" + prepared() + ", finished=" + (finished != null)
                + ", values=redacted}";
    }
}
