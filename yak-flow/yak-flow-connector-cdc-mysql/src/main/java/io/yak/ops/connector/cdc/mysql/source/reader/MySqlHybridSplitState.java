package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import java.util.Objects;

/**
 * Mailbox-confined Snapshot key and Binlog phase/offset for one active Source split.
 *
 * <p>Fetcher prefetch never mutates checkpoint state. Each UPDATE pair advances the
 * Binlog offset only after both TableRecords have been emitted in one mailbox step.
 */
public final class MySqlHybridSplitState {

    private MySqlSnapshotSplit snapshot;
    private MySqlHybridBinlogSplit binlog;
    private BinlogOffset emittedOffset;

    public MySqlHybridSplitState(MySqlHybridSplit initial) {
        if (initial instanceof MySqlSnapshotSplit range) {
            snapshot = range;
        } else if (initial instanceof MySqlHybridBinlogSplit stream) {
            binlog = stream;
            emittedOffset = stream.binlog().offset();
        } else {
            throw new IllegalArgumentException("Unknown MySQL hybrid split");
        }
    }

    public void emitSnapshotKey(Long key) {
        if (snapshot == null || key == null) {
            throw new IllegalStateException("Snapshot progress must contain a key");
        }
        snapshot = snapshot.withLastEmittedKey(key);
    }

    /** Anchors the Binlog BEFORE any JDBC snapshot split may be assigned. */
    public void captureLow(BinlogOffset low, byte[] schemaHistory) {
        if (binlog == null) {
            throw new IllegalStateException("Low watermark is not a Binlog event");
        }
        binlog = binlog.withAnchor(Objects.requireNonNull(low, "low"), schemaHistory);
        emittedOffset = low;
    }

    public void resume(BinlogOffset high) {
        if (binlog == null) {
            return;
        }
        if (binlog.phase() == MySqlHybridBinlogSplit.Phase.STREAMING) {
            return;
        }
        binlog = binlog.startStreaming(high);
    }

    public void emitBinlog(BinlogOffset position) {
        if (binlog == null || binlog.phase() != MySqlHybridBinlogSplit.Phase.STREAMING) {
            throw new IllegalStateException("Binlog changes cannot be emitted before snapshot handoff");
        }
        emittedOffset = Objects.requireNonNull(position, "position");
    }

    public boolean binlog() {
        return binlog != null;
    }

    public MySqlHybridSplit checkpoint(byte[] history) {
        if (snapshot != null) {
            return snapshot;
        }
        if (binlog.phase() == MySqlHybridBinlogSplit.Phase.BOOTSTRAP) {
            throw new IllegalStateException("Cannot checkpoint MySQL hybrid source before its low watermark");
        }
        if (history == null || history.length == 0 || emittedOffset == null) {
            throw new IllegalStateException("Hybrid Binlog checkpoint requires offset and Schema History");
        }
        if (binlog.phase() == MySqlHybridBinlogSplit.Phase.PAUSED) {
            return binlog;
        }
        return binlog.withEmittedOffset(emittedOffset, history);
    }
}
