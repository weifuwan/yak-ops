package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
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
        Set<String> finishedSnapshots) {

    public enum Phase {
        BOOTSTRAP,
        SNAPSHOT,
        HANDOFF,
        STREAMING
    }

    public MySqlHybridEnumeratorState {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(phase, "phase");
        pendingSnapshots = List.copyOf(Objects.requireNonNull(pendingSnapshots, "pendingSnapshots"));
        assignedSnapshots = Map.copyOf(Objects.requireNonNull(assignedSnapshots, "assignedSnapshots"));
        finishedSnapshots = Set.copyOf(Objects.requireNonNull(finishedSnapshots, "finishedSnapshots"));
        if (binlogAssigned == (pendingBinlog != null)
                || totalSnapshotSplits < 0
                || pendingSnapshots.size() + assignedSnapshots.size() + finishedSnapshots.size() != totalSnapshotSplits
                || (phase != Phase.BOOTSTRAP && lowWatermark == null)
                || ((phase == Phase.HANDOFF || phase == Phase.STREAMING) && highWatermark == null)) {
            throw new IllegalArgumentException("Inconsistent MySQL hybrid coordinator state");
        }
    }
}
