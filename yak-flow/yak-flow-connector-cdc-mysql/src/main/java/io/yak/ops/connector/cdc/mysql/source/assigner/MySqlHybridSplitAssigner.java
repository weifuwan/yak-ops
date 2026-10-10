package io.yak.ops.connector.cdc.mysql.source.assigner;

import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlHybridEnumeratorState;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates the hybrid source's four stages without performing JDBC or Binlog I/O.
 *
 * <p>The transition from HANDOFF to STREAMING is gated by a completed checkpoint
 * containing all snapshot output, not by a final split delivery acknowledgment.
 */
public final class MySqlHybridSplitAssigner {

    private final String fingerprint;
    private final MySqlSnapshotSplitAssigner snapshots;
    private final MySqlBinlogSplitAssigner binlog;
    private MySqlHybridEnumeratorState.Phase phase;
    private BinlogOffset low;
    private BinlogOffset high;
    private Long handoffCheckpointId;

    public MySqlHybridSplitAssigner(String fingerprint, MySqlHybridEnumeratorState restored) {
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        if (restored == null) {
            snapshots = new MySqlSnapshotSplitAssigner();
            binlog = new MySqlBinlogSplitAssigner(fingerprint);
            phase = MySqlHybridEnumeratorState.Phase.BOOTSTRAP;
        } else {
            if (!fingerprint.equals(restored.fingerprint())) {
                throw new IllegalArgumentException("MySQL hybrid source definition changed since checkpoint");
            }
            phase = restored.phase();
            low = restored.lowWatermark();
            high = restored.highWatermark();
            binlog = new MySqlBinlogSplitAssigner(restored.pendingBinlog(), restored.binlogAssigned());
            snapshots = new MySqlSnapshotSplitAssigner(
                    restored.pendingSnapshots(),
                    restored.assignedSnapshots(),
                    restored.finishedSnapshots(),
                    restored.totalSnapshotSplits(),
                    restored.snapshotPlanned());
            if (phase == MySqlHybridEnumeratorState.Phase.SNAPSHOT && !snapshots.planned()) {
                throw new IllegalArgumentException("Cannot restore an incomplete snapshot planning checkpoint");
            }
        }
    }

    public MySqlHybridBinlogSplit nextBinlog(int subtaskId) {
        return binlog.next(subtaskId);
    }

    public MySqlSnapshotSplit nextSnapshot() {
        if (phase != MySqlHybridEnumeratorState.Phase.SNAPSHOT) {
            return null;
        }
        return snapshots.next();
    }

    public void captureLow(BinlogOffset offset) {
        if (phase != MySqlHybridEnumeratorState.Phase.BOOTSTRAP) {
            if (!Objects.equals(low, offset)) {
                throw new IllegalStateException("Conflicting MySQL hybrid low watermark");
            }
            return;
        }
        low = Objects.requireNonNull(offset, "offset");
        phase = MySqlHybridEnumeratorState.Phase.SNAPSHOT;
    }

    public void plan(List<MySqlSnapshotSplit> splits) {
        if (phase != MySqlHybridEnumeratorState.Phase.SNAPSHOT) {
            throw new IllegalStateException("Cannot plan snapshot before Binlog anchor");
        }
        snapshots.plan(splits);
    }

    public void finishSnapshot(String splitId) {
        snapshots.complete(splitId);
    }

    public boolean snapshotComplete() {
        return phase == MySqlHybridEnumeratorState.Phase.SNAPSHOT && snapshots.allFinished();
    }

    public void captureHigh(BinlogOffset offset) {
        if (!snapshotComplete()) {
            throw new IllegalStateException("High watermark requires all snapshot splits to finish");
        }
        high = Objects.requireNonNull(offset, "offset");
        phase = MySqlHybridEnumeratorState.Phase.HANDOFF;
    }

    public boolean restoredHandoffReady() {
        return phase == MySqlHybridEnumeratorState.Phase.HANDOFF && handoffCheckpointId == null;
    }

    /**
     * Resumes a HANDOFF state loaded from an already completed durable checkpoint.
     *
     * <p>Fresh in-memory attempts must instead wait for notifyCheckpointComplete.
     */
    public void resumeRestoredHandoff() {
        if (phase != MySqlHybridEnumeratorState.Phase.HANDOFF || handoffCheckpointId != null) {
            throw new IllegalStateException("Expected a restored completed handoff checkpoint");
        }
        phase = MySqlHybridEnumeratorState.Phase.STREAMING;
    }

    public boolean completeCheckpoint(long checkpointId) {
        if (phase == MySqlHybridEnumeratorState.Phase.HANDOFF
                && handoffCheckpointId != null
                && checkpointId >= handoffCheckpointId) {
            phase = MySqlHybridEnumeratorState.Phase.STREAMING;
            handoffCheckpointId = null;
            return true;
        }
        return false;
    }

    public MySqlHybridEnumeratorState snapshot(long checkpointId) {
        if (checkpointId < 0) {
            throw new IllegalArgumentException("Checkpoint ID must not be negative");
        }
        if (phase == MySqlHybridEnumeratorState.Phase.SNAPSHOT && !snapshots.planned()) {
            throw new IllegalStateException("Cannot checkpoint an unfinished MySQL snapshot plan");
        }
        if (phase == MySqlHybridEnumeratorState.Phase.HANDOFF) {
            handoffCheckpointId = checkpointId;
        }
        return new MySqlHybridEnumeratorState(
                fingerprint,
                phase,
                binlog.pending(),
                binlog.assigned(),
                low,
                high,
                snapshots.planned(),
                snapshots.total(),
                snapshots.pending(),
                snapshots.assigned(),
                snapshots.finished());
    }

    public void addBack(MySqlHybridBinlogSplit split) {
        binlog.addBack(split);
    }

    public void addBack(MySqlSnapshotSplit split) {
        snapshots.addBack(split);
    }

    public MySqlHybridEnumeratorState.Phase phase() {
        return phase;
    }

    public BinlogOffset low() {
        return low;
    }

    public BinlogOffset high() {
        return high;
    }

    public boolean snapshotPlanned() {
        return snapshots.planned();
    }
}
