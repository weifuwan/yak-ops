package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.base.source.meta.split.SnapshotSplit;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.data.TableId;
import java.util.Arrays;
import java.util.Objects;

/** Source chunk boundaries and completion high watermark; normalized rows are transient only. */
public final class MySqlSnapshotSplit extends MySqlSplit implements SnapshotSplit {
    private final String splitId;
    private final TableId tableId;
    private final Long lowerInclusive;
    private final Long upperExclusive;
    private final Long lastEmittedKey;
    private final MySqlSnapshotOutput output;
    private final int outputPosition;
    private final FinishedSnapshotSplitInfo finished;
    private final ChunkRange chunkRange;
    private final byte[] finishedSchemaHistory;

    /** Constructs an unprepared legacy numeric range using the stable split identifier. */
    public MySqlSnapshotSplit(
            String splitId,
            TableId tableId,
            Long lowerInclusive,
            Long upperExclusive,
            Long lastEmittedKey,
            MySqlSnapshotOutput output,
            int outputPosition,
            FinishedSnapshotSplitInfo finished) {
        this(splitId, tableId, lowerInclusive, upperExclusive, lastEmittedKey, output, outputPosition, finished, null);
    }

    /** One typed chunk range. Incomplete chunks restart their scan and backfill. */
    public static MySqlSnapshotSplit typed(String splitId, ChunkRange range) {
        Objects.requireNonNull(range, "range");
        return new MySqlSnapshotSplit(splitId, range.tableId(), null, null, null, null, 0, null, range);
    }

    /** Creates an unprepared range; numeric cursors are used only by the JDBC scan primitive. */
    public MySqlSnapshotSplit(
            String splitId, TableId tableId, Long lowerInclusive, Long upperExclusive, Long lastEmittedKey) {
        this(splitId, tableId, lowerInclusive, upperExclusive, lastEmittedKey, null, 0, null);
    }

    public MySqlSnapshotSplit(
            String splitId,
            TableId tableId,
            Long lowerInclusive,
            Long upperExclusive,
            Long lastEmittedKey,
            MySqlSnapshotOutput output,
            int outputPosition,
            FinishedSnapshotSplitInfo finished,
            ChunkRange chunkRange) {
        this(
                splitId,
                tableId,
                lowerInclusive,
                upperExclusive,
                lastEmittedKey,
                output,
                outputPosition,
                finished,
                chunkRange,
                new byte[0]);
    }

    /** Schema history captured by the bounded backfill task through this completed chunk's HIGH. */
    public MySqlSnapshotSplit(
            String splitId,
            TableId tableId,
            Long lowerInclusive,
            Long upperExclusive,
            Long lastEmittedKey,
            MySqlSnapshotOutput output,
            int outputPosition,
            FinishedSnapshotSplitInfo finished,
            ChunkRange chunkRange,
            byte[] finishedSchemaHistory) {
        super(splitId);
        if (splitId == null || splitId.isBlank()) {
            throw new IllegalArgumentException("Snapshot split requires a stable ID");
        }
        Objects.requireNonNull(tableId, "tableId");
        if (chunkRange != null
                && (!tableId.equals(chunkRange.tableId())
                        || lowerInclusive != null
                        || upperExclusive != null
                        || lastEmittedKey != null)) {
            throw new IllegalArgumentException("Typed snapshot cannot reuse legacy BIGINT boundaries or cursor");
        }
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
                        || !Objects.equals(chunkRange, finished.chunkRange())
                        || lastEmittedKey != null)) {
            throw new IllegalArgumentException("Finished snapshot metadata differs from assigned range");
        }

        this.splitId = splitId;
        this.tableId = tableId;
        this.lowerInclusive = lowerInclusive;
        this.upperExclusive = upperExclusive;
        this.lastEmittedKey = lastEmittedKey;
        this.output = output;
        this.outputPosition = outputPosition;
        this.finished = finished;
        this.chunkRange = chunkRange;
        this.finishedSchemaHistory = Objects.requireNonNull(finishedSchemaHistory, "finishedSchemaHistory")
                .clone();
        if (finished == null && this.finishedSchemaHistory.length != 0) {
            throw new IllegalArgumentException("Unfinished snapshot cannot carry completed schema history");
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
                tableId,
                lowerInclusive,
                upperExclusive,
                null,
                Objects.requireNonNull(normalized, "normalized"),
                0,
                null,
                chunkRange);
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
                    tableId,
                    lowerInclusive,
                    upperExclusive,
                    null,
                    null,
                    0,
                    new FinishedSnapshotSplitInfo(
                            tableId, splitId, lowerInclusive, upperExclusive, output.high(), chunkRange),
                    chunkRange,
                    output.schemaHistory());
        }
        return new MySqlSnapshotSplit(
                splitId, tableId, lowerInclusive, upperExclusive, null, output, position, null, chunkRange);
    }

    @Override
    public String splitId() {
        return splitId;
    }

    public TableId tableId() {
        return tableId;
    }

    public Long lowerInclusive() {
        return lowerInclusive;
    }

    public Long upperExclusive() {
        return upperExclusive;
    }

    public Long lastEmittedKey() {
        return lastEmittedKey;
    }

    public MySqlSnapshotOutput output() {
        return output;
    }

    public int outputPosition() {
        return outputPosition;
    }

    public FinishedSnapshotSplitInfo finished() {
        return finished;
    }

    /** Recoverable Debezium history captured through this chunk's High watermark. */
    public byte[] finishedSchemaHistory() {
        return finishedSchemaHistory.clone();
    }

    public ChunkRange chunkRange() {
        return chunkRange;
    }

    // Names and split-boundary semantics follow Flink CDC's MySqlSnapshotSplit.
    public TableId getTableId() {
        return tableId;
    }

    public Object[] getSplitStart() {
        Object start = chunkRange != null ? chunkRange.start() : lowerInclusive;
        return start == null ? null : new Object[] {start};
    }

    public Object[] getSplitEnd() {
        Object end = chunkRange != null ? chunkRange.end() : upperExclusive;
        return end == null ? null : new Object[] {end};
    }

    public DebeziumOffset getHighWatermark() {
        return finished == null ? null : finished.highWatermark();
    }

    public boolean isSnapshotReadFinished() {
        return finished != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MySqlSnapshotSplit that)) {
            return false;
        }
        return outputPosition == that.outputPosition
                && Objects.equals(splitId, that.splitId)
                && Objects.equals(tableId, that.tableId)
                && Objects.equals(lowerInclusive, that.lowerInclusive)
                && Objects.equals(upperExclusive, that.upperExclusive)
                && Objects.equals(lastEmittedKey, that.lastEmittedKey)
                && Objects.equals(output, that.output)
                && Objects.equals(finished, that.finished)
                && Objects.equals(chunkRange, that.chunkRange)
                && Arrays.equals(finishedSchemaHistory, that.finishedSchemaHistory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                splitId,
                tableId,
                lowerInclusive,
                upperExclusive,
                lastEmittedKey,
                output,
                outputPosition,
                finished,
                chunkRange,
                Arrays.hashCode(finishedSchemaHistory));
    }

    @Override
    public String toString() {
        return "MySqlSnapshotSplit{id=" + splitId + ", prepared=" + prepared() + ", finished=" + (finished != null)
                + ", values=redacted}";
    }
}
