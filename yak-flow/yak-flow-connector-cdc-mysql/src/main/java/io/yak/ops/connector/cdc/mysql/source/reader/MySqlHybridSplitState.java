package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import java.util.Objects;

/**
 * Mailbox-confined normalized snapshot output, preparation checkpoint, and Binlog phase/offset.
 *
 * <p>Fetcher prefetch never mutates checkpoint state. Each UPDATE pair advances the
 * Binlog offset only after both TableRecords have been emitted in one mailbox step.
 */
public final class MySqlHybridSplitState {

    private MySqlSnapshotSplit snapshot;
    private MySqlHybridBinlogSplit binlog;
    private BinlogOffset emittedOffset;
    private Long outputCheckpoint;
    private boolean outputDurable;

    public MySqlHybridSplitState(MySqlHybridSplit initial) {
        if (initial instanceof MySqlSnapshotSplit range) {
            snapshot = range;
            outputDurable = range.prepared();
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

    public void prepareSnapshot(MySqlSnapshotSplit prepared) {
        if (snapshot == null
                || snapshot.prepared()
                || !snapshot.splitId().equals(prepared.splitId())
                || !snapshot.tableId().equals(prepared.tableId())
                || !snapshot.fingerprint().equals(prepared.fingerprint())
                || !Objects.equals(snapshot.lowerInclusive(), prepared.lowerInclusive())
                || !Objects.equals(snapshot.upperExclusive(), prepared.upperExclusive())) {
            throw new IllegalStateException("Prepared snapshot differs from its assigned range");
        }
        snapshot = prepared;
        outputDurable = false;
    }

    public void snapshotCheckpoint(long checkpointId) {
        if (snapshot != null && snapshot.output() != null && !outputDurable && outputCheckpoint == null) {
            outputCheckpoint = checkpointId;
        }
    }

    public String completeCheckpoint(long checkpointId) {
        if (outputCheckpoint != null && checkpointId >= outputCheckpoint) {
            outputDurable = true;
            outputCheckpoint = null;
            return snapshot.splitId();
        }
        return null;
    }

    public void emitSnapshotRows(int position) {
        if (!outputDurable) throw new IllegalStateException("Snapshot output has no completed preparation checkpoint");
        snapshot = snapshot.withOutputPosition(position);
    }

    public void resume(
            BinlogOffset high,
            java.util.List<io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo> finished) {
        if (binlog == null) {
            return;
        }
        if (binlog.phase() == MySqlHybridBinlogSplit.Phase.STREAMING) {
            if (!Objects.equals(high, binlog.highWatermark()) || !finished.equals(binlog.finishedSplits())) {
                throw new IllegalStateException("Restored streaming metadata conflicts with coordinator handoff");
            }
            return;
        }
        binlog = binlog.startStreaming(high, finished);
        emittedOffset = binlog.binlog().offset();
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
            return binlog;
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
