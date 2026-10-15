package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffsetOrder;
import java.util.List;
import java.util.Objects;

/**
 * Checkpointable Binlog split with the initial snapshot handoff phase and watermark.
 *
 * <p>BOOTSTRAP captures a resume point before any snapshot is assigned. PAUSED retains
 * bootstrap history while normalized snapshot ranges are checkpointed and emitted.
 * STREAMING starts at the minimum completed chunk High and filters each key by its
 * chunk's High. No Binlog offset advances while paused.
 *
 * <p>Each phase transition returns a new split. The nested Binlog split owns its
 * serialized Schema History bytes and exposes them only through defensive copies.
 * An assigned split has execution identity, rather than implicit record value equality.
 */
public final class MySqlHybridBinlogSplit implements MySqlHybridSplit {

    public static final String ID = MySqlBinlogSplit.ID;

    /** Snapshot handoff phases of the Binlog subscription. */
    public enum Phase {
        BOOTSTRAP,
        PAUSED,
        STREAMING
    }

    private final MySqlBinlogSplit binlog;
    private final Phase phase;
    private final BinlogOffset highWatermark;
    private final List<FinishedSnapshotSplitInfo> finishedSplits;

    /**
     * Creates one immutable Binlog handoff state.
     *
     * @param binlog Binlog subscription and checkpoint state
     * @param phase current snapshot handoff phase
     * @param highWatermark completed snapshot boundary, when known
     */
    public MySqlHybridBinlogSplit(MySqlBinlogSplit binlog, Phase phase, BinlogOffset highWatermark) {
        this(binlog, phase, highWatermark, List.of());
    }

    public MySqlHybridBinlogSplit(
            MySqlBinlogSplit binlog,
            Phase phase,
            BinlogOffset highWatermark,
            List<FinishedSnapshotSplitInfo> finishedSplits) {
        this.finishedSplits = List.copyOf(Objects.requireNonNull(finishedSplits, "finishedSplits"));
        this.binlog = Objects.requireNonNull(binlog, "binlog");
        this.phase = Objects.requireNonNull(phase, "phase");
        if (phase != Phase.BOOTSTRAP && (binlog.offset() == null || binlog.schemaHistory().length == 0)) {
            throw new IllegalArgumentException("Snapshot handoff requires a Binlog anchor and schema history");
        }
        if (phase == Phase.STREAMING) {
            if (highWatermark == null || this.finishedSplits.isEmpty()) {
                throw new IllegalArgumentException("Streaming Hybrid state requires completed chunk watermarks");
            }
            var ids = new java.util.HashSet<String>();
            BinlogOffset maximum = null;
            for (FinishedSnapshotSplitInfo info : this.finishedSplits) {
                if (!ids.add(info.splitId())) throw new IllegalArgumentException("Duplicate streaming chunk identity");
                BinlogOffsetOrder.compare(binlog.offset(), info.highWatermark());
                if (maximum == null || BinlogOffsetOrder.compare(info.highWatermark(), maximum) > 0) {
                    maximum = info.highWatermark();
                }
            }
            if (BinlogOffsetOrder.compare(highWatermark, maximum) != 0) {
                throw new IllegalArgumentException("Hybrid handoff High differs from completed chunk metadata");
            }
        } else if (highWatermark != null || !this.finishedSplits.isEmpty()) {
            throw new IllegalArgumentException("Unfinished Hybrid state must not contain streaming metadata");
        }
        this.highWatermark = highWatermark;
    }

    public List<FinishedSnapshotSplitInfo> finishedSplits() {
        return finishedSplits;
    }

    /** Returns the underlying Binlog split. */
    public MySqlBinlogSplit binlog() {
        return binlog;
    }

    /** Returns the current snapshot handoff phase. */
    public Phase phase() {
        return phase;
    }

    /** Returns the completed snapshot boundary, when available. */
    public BinlogOffset highWatermark() {
        return highWatermark;
    }

    /** Returns the stable identifier of the Binlog subscription. */
    @Override
    public String splitId() {
        return ID;
    }

    /** Captures the low watermark and schema history before snapshot assignment. */
    public MySqlHybridBinlogSplit withAnchor(BinlogOffset offset, byte[] history) {
        if (phase != Phase.BOOTSTRAP) {
            throw new IllegalStateException("Hybrid Binlog anchor has already been captured");
        }
        return new MySqlHybridBinlogSplit(binlog.withProgress(offset, history), Phase.PAUSED, null);
    }

    /** Resumes streaming after a completed snapshot checkpoint. */
    public MySqlHybridBinlogSplit startStreaming(BinlogOffset high, List<FinishedSnapshotSplitInfo> completed) {
        if (phase != Phase.PAUSED) {
            throw new IllegalStateException("Hybrid Binlog is not waiting for snapshot handoff");
        }
        if (completed.isEmpty()) throw new IllegalArgumentException("Snapshot handoff requires per-chunk watermarks");
        BinlogOffset start = completed.stream()
                .map(FinishedSnapshotSplitInfo::highWatermark)
                .min(BinlogOffsetOrder::compare)
                .orElseThrow();
        return new MySqlHybridBinlogSplit(
                binlog.withProgress(start, binlog.schemaHistory()),
                Phase.STREAMING,
                Objects.requireNonNull(high, "high"),
                completed);
    }

    /** Advances the checkpoint cursor after the emitted Binlog change is acknowledged. */
    public MySqlHybridBinlogSplit withEmittedOffset(BinlogOffset offset, byte[] history) {
        if (phase != Phase.STREAMING) {
            throw new IllegalStateException("A paused Binlog split must not advance its offset");
        }
        return new MySqlHybridBinlogSplit(binlog.withProgress(offset, history), phase, highWatermark, finishedSplits);
    }

    /** Excludes checkpoint offsets and Schema History from diagnostic output. */
    @Override
    public String toString() {
        return "MySqlHybridBinlogSplit{phase=" + phase + ", state=redacted}";
    }
}
