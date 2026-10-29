package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.base.source.meta.split.StreamSplit;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.utils.RecordUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The binlog split of MySQL source.
 *
 * <p>In the initial mode the binlog split is suspended while snapshot splits are read.
 * Once all snapshot splits finish, the reader resumes at the earliest chunk high watermark
 * and filters binlog events according to the finished snapshot split information.
 *
 * <p>Debezium schema history travels with the offset until schema recovery is moved to the
 * common runtime; a restored offset without its history is not a valid running split.
 */
public final class MySqlBinlogSplit extends MySqlSplit implements StreamSplit {

    public static final String ID = "mysql-binlog";

    /** Initial snapshot handoff and standalone streaming share the same split type. */
    public enum Phase {
        STANDALONE,
        BOOTSTRAP,
        PAUSED,
        STREAMING
    }

    private final DebeziumOffset offset;
    private final byte[] schemaHistory;
    private final Phase phase;
    private final DebeziumOffset highWatermark;
    private final List<FinishedSnapshotSplitInfo> finishedSnapshotSplitInfos;
    private final int totalFinishedSplitSize;

    /** Constructs a binlog-only source split. */
    public MySqlBinlogSplit(DebeziumOffset offset, byte[] schemaHistory) {
        this(offset, schemaHistory, Phase.STANDALONE, null, List.of());
    }

    /** Constructs the suspended bootstrap split for initial snapshot. */
    public static MySqlBinlogSplit forInitialSnapshot() {
        return new MySqlBinlogSplit(null, new byte[0], Phase.BOOTSTRAP, null, List.of());
    }

    /** Restores an anchored initial split without a second wrapper type. */
    public static MySqlBinlogSplit forPausedSnapshot(DebeziumOffset low, byte[] schemaHistory) {
        return forInitialSnapshot().withAnchor(low, schemaHistory);
    }

    /** Builds the completed streaming split from its per-chunk high watermarks. */
    public static MySqlBinlogSplit forCompletedSnapshot(
            DebeziumOffset startingOffset,
            byte[] schemaHistory,
            DebeziumOffset highWatermark,
            List<FinishedSnapshotSplitInfo> finished) {
        return new MySqlBinlogSplit(startingOffset, schemaHistory, Phase.STREAMING, highWatermark, finished);
    }

    /** Flink CDC 3.7 sends large finished-split metadata in bounded groups. */
    public static MySqlBinlogSplit forCompletedSnapshot(
            DebeziumOffset startingOffset,
            byte[] schemaHistory,
            DebeziumOffset highWatermark,
            List<FinishedSnapshotSplitInfo> finished,
            int totalFinishedSplitSize) {
        return new MySqlBinlogSplit(
                startingOffset, schemaHistory, Phase.STREAMING, highWatermark, finished, totalFinishedSplitSize);
    }

    public MySqlBinlogSplit withFinishedSnapshotSplitInfos(List<FinishedSnapshotSplitInfo> finished) {
        return new MySqlBinlogSplit(offset, schemaHistory, phase, highWatermark, finished, totalFinishedSplitSize);
    }

    private MySqlBinlogSplit(
            DebeziumOffset offset,
            byte[] schemaHistory,
            Phase phase,
            DebeziumOffset highWatermark,
            List<FinishedSnapshotSplitInfo> finishedSnapshotSplitInfos) {
        this(
                offset,
                schemaHistory,
                phase,
                highWatermark,
                finishedSnapshotSplitInfos,
                finishedSnapshotSplitInfos.size());
    }

    private MySqlBinlogSplit(
            DebeziumOffset offset,
            byte[] schemaHistory,
            Phase phase,
            DebeziumOffset highWatermark,
            List<FinishedSnapshotSplitInfo> finishedSnapshotSplitInfos,
            int totalFinishedSplitSize) {
        super(ID);
        this.offset = offset;
        this.schemaHistory =
                Objects.requireNonNull(schemaHistory, "schemaHistory").clone();
        this.phase = Objects.requireNonNull(phase, "phase");
        this.finishedSnapshotSplitInfos =
                List.copyOf(Objects.requireNonNull(finishedSnapshotSplitInfos, "finishedSnapshotSplitInfos"));
        if (totalFinishedSplitSize < this.finishedSnapshotSplitInfos.size() || totalFinishedSplitSize > 10_000) {
            throw new IllegalArgumentException("Invalid Binlog finished snapshot split count");
        }
        this.totalFinishedSplitSize = totalFinishedSplitSize;
        if (phase == Phase.PAUSED || phase == Phase.STREAMING) {
            if (offset == null || this.schemaHistory.length == 0) {
                throw new IllegalArgumentException("Initial Binlog requires anchor and Debezium schema history");
            }
        }
        if (phase == Phase.STREAMING || (phase == Phase.PAUSED && totalFinishedSplitSize > 0)) {
            if (highWatermark == null || totalFinishedSplitSize == 0) {
                throw new IllegalArgumentException("Streaming Binlog requires completed snapshot watermarks");
            }
            HashSet<String> ids = new HashSet<>();
            DebeziumOffset max = null;
            for (FinishedSnapshotSplitInfo info : this.finishedSnapshotSplitInfos) {
                if (!ids.add(info.splitId())) {
                    throw new IllegalArgumentException("Duplicate finished snapshot split ID");
                }
                offset.compareTo(info.highWatermark());
                if (max == null || info.highWatermark().compareTo(max) > 0) {
                    max = info.highWatermark();
                }
            }
            if (totalFinishedSplitSize == this.finishedSnapshotSplitInfos.size() && highWatermark.compareTo(max) != 0) {
                throw new IllegalArgumentException("Completed snapshot high watermark mismatch");
            }
        } else if (highWatermark != null || totalFinishedSplitSize != 0) {
            throw new IllegalArgumentException("Unfinished Binlog cannot contain completed snapshot metadata");
        }
        this.highWatermark = highWatermark;
    }

    public DebeziumOffset offset() {
        return offset;
    }

    /** The starting offset of the binlog split. */
    public DebeziumOffset getStartingOffset() {
        return offset;
    }

    public byte[] schemaHistory() {
        return schemaHistory.clone();
    }

    public Phase phase() {
        return phase;
    }

    public boolean isSuspended() {
        return phase == Phase.BOOTSTRAP || phase == Phase.PAUSED;
    }

    public boolean isCompletedSplit() {
        return phase == Phase.STANDALONE
                || (phase == Phase.STREAMING && finishedSnapshotSplitInfos.size() == totalFinishedSplitSize);
    }

    public int getTotalFinishedSplitSize() {
        return totalFinishedSplitSize;
    }

    public List<FinishedSnapshotSplitInfo> getFinishedSnapshotSplitInfos() {
        return finishedSnapshotSplitInfos;
    }

    public List<FinishedSnapshotSplitInfo> finishedSplits() {
        return finishedSnapshotSplitInfos;
    }

    public DebeziumOffset highWatermark() {
        return highWatermark;
    }

    public DebeziumOffset getHighWatermark() {
        return highWatermark;
    }

    /** Captures the binlog low watermark and schema history before any snapshot split runs. */
    public MySqlBinlogSplit withAnchor(DebeziumOffset low, byte[] history) {
        if (phase != Phase.BOOTSTRAP) {
            throw new IllegalStateException("Binlog low watermark is already anchored");
        }
        return new MySqlBinlogSplit(Objects.requireNonNull(low, "low"), history, Phase.PAUSED, null, List.of());
    }

    /** Resumes the suspended binlog when snapshot completion has been durably acknowledged. */
    public MySqlBinlogSplit startStreaming(DebeziumOffset high, List<FinishedSnapshotSplitInfo> completed) {
        if (phase != Phase.PAUSED) {
            throw new IllegalStateException("Binlog split is not suspended for snapshot handoff");
        }
        if (completed.isEmpty()) {
            throw new IllegalArgumentException("No finished snapshot watermarks");
        }
        if (!finishedSnapshotSplitInfos.isEmpty()) {
            // Newly added tables extend the fence metadata while the Binlog is paused. Keep
            // the exact offset captured at suspension; restarting from the oldest new fence
            // would replay already emitted changes and could violate the reader's checkpoint.
            return new MySqlBinlogSplit(
                    offset, schemaHistory, Phase.STREAMING, Objects.requireNonNull(high, "high"), completed);
        }
        DebeziumOffset start = RecordUtils.getStartingOffsetOfBinlogSplit(completed);
        return new MySqlBinlogSplit(
                start, schemaHistory, Phase.STREAMING, Objects.requireNonNull(high, "high"), completed);
    }

    /** Advances only after the emitted binlog event is acknowledged by the mailbox. */
    public MySqlBinlogSplit withEmittedOffset(DebeziumOffset next, byte[] history) {
        if (phase != Phase.STREAMING) {
            throw new IllegalStateException("A suspended Binlog must not advance its offset");
        }
        return new MySqlBinlogSplit(
                Objects.requireNonNull(next, "next"), history, phase, highWatermark, finishedSnapshotSplitInfos);
    }

    public MySqlBinlogSplit withProgress(DebeziumOffset next, byte[] history) {
        if (phase != Phase.STANDALONE) {
            throw new IllegalStateException("Snapshot streaming uses withEmittedOffset");
        }
        return new MySqlBinlogSplit(Objects.requireNonNull(next, "next"), history);
    }

    /** Flink-style view of the resumed binlog split, keeping all finished chunk fences. */
    public static MySqlBinlogSplit toNormalBinlogSplit(MySqlBinlogSplit split) {
        if (split.phase == Phase.STANDALONE || split.phase == Phase.STREAMING) {
            return split;
        }
        if (split.phase == Phase.PAUSED && !split.finishedSnapshotSplitInfos.isEmpty()) {
            return new MySqlBinlogSplit(
                    split.offset,
                    split.schemaHistory,
                    Phase.STREAMING,
                    split.highWatermark,
                    split.finishedSnapshotSplitInfos);
        }
        throw new IllegalStateException("Snapshot handoff must complete before Binlog is resumed");
    }

    /** Suspends an already streaming binlog without discarding its completed snapshot fences. */
    public static MySqlBinlogSplit toSuspendedBinlogSplit(MySqlBinlogSplit split) {
        if (split.phase == Phase.PAUSED || split.phase == Phase.BOOTSTRAP) {
            return split;
        }
        if (split.phase == Phase.STANDALONE) {
            throw new IllegalStateException("Binlog-only sources do not use snapshot handoff");
        }
        return new MySqlBinlogSplit(
                split.offset, split.schemaHistory, Phase.PAUSED, split.highWatermark, split.finishedSnapshotSplitInfos);
    }

    /** Used only by the source split serializer to reconstruct validated immutable state. */
    static MySqlBinlogSplit restore(
            DebeziumOffset offset,
            byte[] history,
            Phase phase,
            DebeziumOffset high,
            List<FinishedSnapshotSplitInfo> infos,
            int totalFinishedSplitSize) {
        return new MySqlBinlogSplit(offset, history, phase, high, infos, totalFinishedSplitSize);
    }

    @Override
    public String toString() {
        return "MySqlBinlogSplit{phase=" + phase + ", state=redacted}";
    }
}
