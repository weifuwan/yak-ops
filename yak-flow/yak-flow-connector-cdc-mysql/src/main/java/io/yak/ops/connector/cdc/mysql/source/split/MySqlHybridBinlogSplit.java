package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import java.util.Objects;

/**
 * Wraps the PR1 Binlog split with a checkpointed hybrid snapshot gate.
 *
 * <p>BOOTSTRAP captures a resume point before any snapshot is assigned; PAUSED preserves
 * that point while snapshot ranges are emitted; STREAMING replays Binlog from the anchor
 * after a completed snapshot checkpoint. No Binlog offset advances while paused.
 */
public record MySqlHybridBinlogSplit(MySqlBinlogSplit binlog, Phase phase, BinlogOffset highWatermark)
        implements MySqlHybridSplit {

    public enum Phase {
        BOOTSTRAP,
        PAUSED,
        STREAMING
    }

    public MySqlHybridBinlogSplit {
        Objects.requireNonNull(binlog, "binlog");
        Objects.requireNonNull(phase, "phase");
        if (phase != Phase.BOOTSTRAP && (binlog.offset() == null || binlog.schemaHistory().length == 0)) {
            throw new IllegalArgumentException("Snapshot handoff requires a Binlog anchor and schema history");
        }
    }

    @Override
    public String splitId() {
        return MySqlBinlogSplit.ID;
    }

    public MySqlHybridBinlogSplit withAnchor(BinlogOffset offset, byte[] history) {
        if (phase != Phase.BOOTSTRAP) {
            throw new IllegalStateException("Hybrid Binlog anchor has already been captured");
        }
        return new MySqlHybridBinlogSplit(binlog.withProgress(offset, history), Phase.PAUSED, null);
    }

    public MySqlHybridBinlogSplit startStreaming(BinlogOffset high) {
        if (phase != Phase.PAUSED) {
            throw new IllegalStateException("Hybrid Binlog is not waiting for snapshot handoff");
        }
        return new MySqlHybridBinlogSplit(binlog, Phase.STREAMING, Objects.requireNonNull(high, "high"));
    }

    public MySqlHybridBinlogSplit withEmittedOffset(BinlogOffset offset, byte[] history) {
        if (phase != Phase.STREAMING) {
            throw new IllegalStateException("A paused Binlog split must not advance its offset");
        }
        return new MySqlHybridBinlogSplit(binlog.withProgress(offset, history), phase, highWatermark);
    }
}
