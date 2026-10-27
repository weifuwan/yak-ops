package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.cdc.mysql.source.assigner.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Checkpointed global progress of the initial snapshot and deferred Binlog handoff.
 *
 * <p>The checkpoint covering HANDOFF must complete before the streaming Reader is
 * unpaused. Coordinator-assigned snapshot metadata never replaces Reader-owned cursors.
 */
public record MySqlHybridEnumeratorState(
        String fingerprint,
        Phase phase,
        MySqlHybridBinlogSplit pendingBinlog,
        boolean binlogAssigned,
        BinlogOffset lowWatermark,
        BinlogOffset highWatermark,
        boolean snapshotPlanned,
        int totalSnapshotSplits,
        List<MySqlSnapshotSplit> pendingSnapshots,
        Map<String, MySqlSnapshotSplit> assignedSnapshots,
        Set<String> finishedSnapshots,
        List<FinishedSnapshotSplitInfo> finishedSplitInfos,
        int nextTableIndex,
        int nextChunkIndex,
        ChunkRange nextChunkRange)
        implements PendingSplitsState {

    public enum Phase {
        BOOTSTRAP,
        SNAPSHOT,
        HANDOFF,
        STREAMING
    }

    public MySqlHybridEnumeratorState {
        Objects.requireNonNull(fingerprint, "fingerprint");
        finishedSplitInfos = List.copyOf(Objects.requireNonNull(finishedSplitInfos, "finishedSplitInfos"));
        if (nextChunkRange != null
                && (nextChunkIndex < 1 || nextChunkRange.start() == null || nextChunkRange.end() != null)) {
            throw new IllegalArgumentException("Invalid typed chunk discovery cursor");
        }
        if (nextTableIndex < 0
                || nextChunkIndex < 0
                || nextChunkIndex > 10_000
                || (nextChunkIndex > 0 && nextChunkRange == null)) {
            throw new IllegalArgumentException("Invalid snapshot discovery position");
        }
        Objects.requireNonNull(phase, "phase");
        pendingSnapshots = List.copyOf(Objects.requireNonNull(pendingSnapshots, "pendingSnapshots"));
        assignedSnapshots = Map.copyOf(Objects.requireNonNull(assignedSnapshots, "assignedSnapshots"));
        finishedSnapshots = Set.copyOf(Objects.requireNonNull(finishedSnapshots, "finishedSnapshots"));
        Set<String> completedIds = new java.util.HashSet<>();
        for (FinishedSnapshotSplitInfo info : finishedSplitInfos) {
            if (!completedIds.add(info.splitId()))
                throw new IllegalArgumentException("Duplicate completed chunk metadata");
        }
        if (!completedIds.equals(finishedSnapshots))
            throw new IllegalArgumentException("Incomplete chunk High metadata");
        Set<String> allIds = new java.util.HashSet<>(finishedSnapshots);
        for (MySqlSnapshotSplit split : pendingSnapshots) {
            if (!allIds.add(split.splitId())) throw new IllegalArgumentException("Duplicate pending chunk");
        }
        for (var entry : assignedSnapshots.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().splitId()) || !allIds.add(entry.getKey())) {
                throw new IllegalArgumentException("Duplicate or invalid assigned chunk");
            }
        }
        if (phase == Phase.HANDOFF || phase == Phase.STREAMING) {
            if (!snapshotPlanned
                    || totalSnapshotSplits == 0
                    || finishedSnapshots.size() != totalSnapshotSplits
                    || highWatermark == null
                    || lowWatermark == null) {
                throw new IllegalArgumentException("Hybrid handoff requires all normalized chunks to be completed");
            }
            BinlogOffset maximum = finishedSplitInfos.stream()
                    .map(FinishedSnapshotSplitInfo::highWatermark)
                    .max(BinlogOffset::compareTo)
                    .orElseThrow();
            if (highWatermark.compareTo(maximum) != 0 || lowWatermark.compareTo(highWatermark) > 0) {
                throw new IllegalArgumentException("Hybrid coordinator watermarks differ from completed chunks");
            }
        }
        if (binlogAssigned == (pendingBinlog != null)
                || totalSnapshotSplits < 0
                || totalSnapshotSplits > 10_000
                || pendingSnapshots.size() + assignedSnapshots.size() + finishedSnapshots.size() != totalSnapshotSplits
                || (phase != Phase.BOOTSTRAP && lowWatermark == null)
                || ((phase == Phase.HANDOFF || phase == Phase.STREAMING) && highWatermark == null)) {
            throw new IllegalArgumentException("Inconsistent MySQL hybrid coordinator state");
        }
    }

    @Override
    public String toString() {
        return "MySqlHybridEnumeratorState{phase=" + phase + ", chunks=" + totalSnapshotSplits
                + ", checkpoint=redacted}";
    }
}
