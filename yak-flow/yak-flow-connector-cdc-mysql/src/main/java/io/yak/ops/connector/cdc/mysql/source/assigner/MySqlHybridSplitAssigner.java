package io.yak.ops.connector.cdc.mysql.source.assigner;

import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlHybridEnumeratorState;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<String, FinishedSnapshotSplitInfo> completed = new LinkedHashMap<>();
    private int nextTableIndex;
    private int nextChunkIndex;
    private Long nextChunkLower;

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
            restored.finishedSplitInfos().forEach(info -> completed.put(info.splitId(), info));
            nextTableIndex = restored.nextTableIndex();
            nextChunkIndex = restored.nextChunkIndex();
            nextChunkLower = restored.nextChunkLower();
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

    public void finishSnapshot(FinishedSnapshotSplitInfo info) {
        FinishedSnapshotSplitInfo previous = completed.get(info.splitId());
        if (previous != null) {
            if (!previous.equals(info)) throw new IllegalStateException("Conflicting completed chunk High watermark");
            return;
        }
        MySqlSnapshotSplit original = snapshots.assigned().get(info.splitId());
        if (original == null
                || !original.tableId().equals(info.tableId())
                || !Objects.equals(original.lowerInclusive(), info.lowerInclusive())
                || !Objects.equals(original.upperExclusive(), info.upperExclusive())) {
            throw new IllegalArgumentException("Completed chunk differs from its assigned range");
        }
        snapshots.complete(info.splitId());
        completed.put(info.splitId(), info);
    }

    public void appendPlan(MySqlSnapshotSplit split, int table, int chunk, Long lower, boolean last) {
        if (phase != MySqlHybridEnumeratorState.Phase.SNAPSHOT)
            throw new IllegalStateException("Snapshot discovery is inactive");
        snapshots.append(List.of(split), last);
        nextTableIndex = table;
        nextChunkIndex = chunk;
        nextChunkLower = lower;
    }

    public List<FinishedSnapshotSplitInfo> finishedSplits() {
        return List.copyOf(completed.values());
    }

    public int nextTableIndex() {
        return nextTableIndex;
    }

    public int nextChunkIndex() {
        return nextChunkIndex;
    }

    public Long nextChunkLower() {
        return nextChunkLower;
    }

    public int pendingSnapshotCount() {
        return snapshots.pending().size();
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
        if (phase == MySqlHybridEnumeratorState.Phase.HANDOFF && handoffCheckpointId == null) {
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
                snapshots.finished(),
                finishedSplits(),
                nextTableIndex,
                nextChunkIndex,
                nextChunkLower);
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
