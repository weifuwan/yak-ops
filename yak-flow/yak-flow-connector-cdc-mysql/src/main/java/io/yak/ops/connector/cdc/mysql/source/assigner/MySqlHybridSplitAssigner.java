package io.yak.ops.connector.cdc.mysql.source.assigner;

import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlHybridEnumeratorState;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
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
public final class MySqlHybridSplitAssigner
        implements MySqlSplitAssigner<MySqlHybridSplit, MySqlHybridEnumeratorState> {

    private final String fingerprint;
    private final MySqlSnapshotSplitAssigner snapshots;
    private final MySqlHybridBinlogSplitAssigner binlog;
    private MySqlHybridEnumeratorState.Phase phase;
    private BinlogOffset low;
    private BinlogOffset high;
    private Long handoffCheckpointId;
    private final Map<String, FinishedSnapshotSplitInfo> completed = new LinkedHashMap<>();
    private int nextTableIndex;
    private int nextChunkIndex;
    private ChunkRange nextChunkRange;

    public MySqlHybridSplitAssigner(String fingerprint, MySqlHybridEnumeratorState restored) {
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        if (restored == null) {
            snapshots = new MySqlSnapshotSplitAssigner();
            binlog = new MySqlHybridBinlogSplitAssigner(fingerprint);
            phase = MySqlHybridEnumeratorState.Phase.BOOTSTRAP;
        } else {
            if (!fingerprint.equals(restored.fingerprint())) {
                throw new IllegalArgumentException("MySQL hybrid source definition changed since checkpoint");
            }
            restored.finishedSplitInfos().forEach(info -> completed.put(info.splitId(), info));
            nextTableIndex = restored.nextTableIndex();
            nextChunkIndex = restored.nextChunkIndex();
            nextChunkRange = restored.nextChunkRange();
            phase = restored.phase();
            low = restored.lowWatermark();
            high = restored.highWatermark();
            binlog = new MySqlHybridBinlogSplitAssigner(restored.pendingBinlog(), restored.binlogAssigned());
            snapshots = new MySqlSnapshotSplitAssigner(
                    restored.pendingSnapshots(),
                    restored.assignedSnapshots(),
                    restored.finishedSnapshots(),
                    restored.totalSnapshotSplits(),
                    restored.snapshotPlanned());
        }
    }

    @Override
    public MySqlHybridSplit getNext(int subtaskId) {
        MySqlHybridBinlogSplit next = nextBinlog(subtaskId);
        return next != null ? next : nextSnapshot();
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
                || !Objects.equals(original.chunkRange(), info.chunkRange())
                || !Objects.equals(original.lowerInclusive(), info.lowerInclusive())
                || !Objects.equals(original.upperExclusive(), info.upperExclusive())) {
            throw new IllegalArgumentException("Completed chunk differs from its assigned range");
        }
        snapshots.complete(info.splitId());
        completed.put(info.splitId(), info);
    }

    /** Atomically persist one discovered range and advance the durable discovery cursor. */
    public void appendPlan(MySqlSnapshotSplit split, int tableCount) {
        ChunkRange range = split.chunkRange();
        if (phase != MySqlHybridEnumeratorState.Phase.SNAPSHOT
                || tableCount < 1
                || nextTableIndex < 0
                || nextTableIndex >= tableCount
                || range == null) {
            throw new IllegalStateException("Invalid snapshot discovery transition");
        }
        if ((nextChunkIndex == 0 && (nextChunkRange != null || range.start() != null))
                || (nextChunkIndex > 0
                        && (nextChunkRange == null
                                || nextChunkRange.end() != null
                                || !nextChunkRange.tableId().equals(range.tableId())
                                || !nextChunkRange.column().equals(range.column())
                                || nextChunkRange.type() != range.type()
                                || !Objects.equals(nextChunkRange.start(), range.start())))) {
            throw new IllegalStateException("Snapshot range differs from the durable discovery cursor");
        }

        boolean tableComplete = range.last();
        int nextTable = tableComplete ? nextTableIndex + 1 : nextTableIndex;
        int nextChunk = tableComplete ? 0 : nextChunkIndex + 1;
        ChunkRange nextRange =
                tableComplete ? null : new ChunkRange(range.tableId(), range.column(), range.type(), range.end(), null);
        snapshots.append(List.of(split), nextTable == tableCount);
        nextTableIndex = nextTable;
        nextChunkIndex = nextChunk;
        nextChunkRange = nextRange;
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

    public ChunkRange nextChunkRange() {
        return nextChunkRange;
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

    @Override
    public boolean notifyCheckpointComplete(long checkpointId) {
        if (phase == MySqlHybridEnumeratorState.Phase.HANDOFF
                && handoffCheckpointId != null
                && checkpointId >= handoffCheckpointId) {
            phase = MySqlHybridEnumeratorState.Phase.STREAMING;
            handoffCheckpointId = null;
            return true;
        }
        return false;
    }

    @Override
    public MySqlHybridEnumeratorState snapshotState(long checkpointId) {
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
                nextChunkRange);
    }

    @Override
    public void addSplitsBack(List<MySqlHybridSplit> splits, int subtaskId) {
        for (MySqlHybridSplit split : splits) {
            if (split instanceof MySqlSnapshotSplit snapshot) {
                addBack(snapshot);
            } else if (split instanceof MySqlHybridBinlogSplit binlogSplit) {
                if (subtaskId != 0) {
                    throw new IllegalArgumentException("Binlog belongs to reader zero");
                }
                addBack(binlogSplit);
            }
        }
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
