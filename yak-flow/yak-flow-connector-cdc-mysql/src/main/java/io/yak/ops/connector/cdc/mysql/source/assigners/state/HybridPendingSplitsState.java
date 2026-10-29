package io.yak.ops.connector.cdc.mysql.source.assigners.state;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.AssignerStatus;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import java.util.Arrays;
import java.util.Objects;

/** A {@link PendingSplitsState} for pending hybrid (snapshot & binlog) splits. */
public final class HybridPendingSplitsState extends PendingSplitsState {

    public enum Phase {
        BOOTSTRAP,
        SNAPSHOT,
        HANDOFF,
        STREAMING
    }

    private final SnapshotPendingSplitsState snapshotPendingSplits;
    private final boolean isBinlogSplitAssigned;
    private final MySqlBinlogSplit pendingBinlog;
    private final Phase phase;
    private final DebeziumOffset lowWatermark;
    private final DebeziumOffset highWatermark;
    private final DebeziumOffset schemaHistoryWatermark;
    private final byte[] schemaHistory;

    public HybridPendingSplitsState(
            SnapshotPendingSplitsState snapshotPendingSplits,
            boolean binlogAssigned,
            MySqlBinlogSplit pendingBinlog,
            Phase phase,
            DebeziumOffset lowWatermark,
            DebeziumOffset highWatermark) {
        this(
                snapshotPendingSplits,
                binlogAssigned,
                pendingBinlog,
                phase,
                lowWatermark,
                highWatermark,
                null,
                new byte[0]);
    }

    public HybridPendingSplitsState(
            SnapshotPendingSplitsState snapshotPendingSplits,
            boolean binlogAssigned,
            MySqlBinlogSplit pendingBinlog,
            Phase phase,
            DebeziumOffset lowWatermark,
            DebeziumOffset highWatermark,
            DebeziumOffset schemaHistoryWatermark,
            byte[] schemaHistory) {
        this.schemaHistoryWatermark = schemaHistoryWatermark;
        this.schemaHistory =
                Objects.requireNonNull(schemaHistory, "schemaHistory").clone();
        if ((schemaHistoryWatermark == null) != (this.schemaHistory.length == 0)) {
            throw new IllegalArgumentException("Snapshot schema history requires its originating HIGH watermark");
        }
        this.snapshotPendingSplits = Objects.requireNonNull(snapshotPendingSplits, "snapshotPendingSplits");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.isBinlogSplitAssigned = binlogAssigned;
        this.pendingBinlog = pendingBinlog;
        this.lowWatermark = lowWatermark;
        this.highWatermark = highWatermark;
        if ((binlogAssigned && pendingBinlog != null)
                || ((phase == Phase.HANDOFF || phase == Phase.STREAMING) && highWatermark == null)) {
            throw new IllegalArgumentException("Inconsistent hybrid binlog assignment state");
        }
        if (phase == Phase.HANDOFF || phase == Phase.STREAMING) {
            AssignerStatus status = snapshotPendingSplits.getSnapshotAssignerStatus();
            boolean released = snapshotPendingSplits.isSnapshotMetaReleased();
            if (released) {
                if (phase != Phase.STREAMING
                        || !snapshotPendingSplits.snapshotPlanned()
                        || !snapshotPendingSplits.getRemainingSplits().isEmpty()
                        || !snapshotPendingSplits.getAssignedSplits().isEmpty()
                        || !snapshotPendingSplits.getFinishedSplitInfos().isEmpty()
                        || !AssignerStatus.isAssigningFinished(status)) {
                    throw new IllegalArgumentException("Released Snapshot metadata has an invalid hybrid phase");
                }
            } else if (phase == Phase.HANDOFF
                    || status == AssignerStatus.INITIAL_ASSIGNING
                    || status == AssignerStatus.INITIAL_ASSIGNING_FINISHED
                    || status == AssignerStatus.NEWLY_ADDED_ASSIGNING_SNAPSHOT_FINISHED
                    || status == AssignerStatus.NEWLY_ADDED_ASSIGNING_FINISHED) {
                if (!snapshotPendingSplits.snapshotPlanned()
                        || snapshotPendingSplits.totalSnapshotSplits() == 0
                        || snapshotPendingSplits.getSplitFinishedOffsets().size()
                                != snapshotPendingSplits.totalSnapshotSplits()) {
                    throw new IllegalArgumentException("Hybrid handoff requires all snapshot chunks");
                }
                DebeziumOffset maximum = snapshotPendingSplits.getSplitFinishedOffsets().values().stream()
                        .max(DebeziumOffset::compareTo)
                        .orElseThrow();
                if (highWatermark.compareTo(maximum) != 0
                        || (lowWatermark != null && lowWatermark.compareTo(highWatermark) > 0)) {
                    throw new IllegalArgumentException("Hybrid checkpoint watermarks are inconsistent");
                }
            } else if (lowWatermark != null && lowWatermark.compareTo(highWatermark) > 0) {
                // During a newly-added-table scan the existing Binlog High watermark remains
                // authoritative until the new table Snapshot is checkpointed and handed off.
                throw new IllegalArgumentException("Hybrid checkpoint watermarks are inconsistent");
            }
        }
    }

    public SnapshotPendingSplitsState getSnapshotPendingSplits() {
        return snapshotPendingSplits;
    }

    public boolean isBinlogSplitAssigned() {
        return isBinlogSplitAssigned;
    }

    public MySqlBinlogSplit pendingBinlog() {
        return pendingBinlog;
    }

    public Phase phase() {
        return phase;
    }

    public DebeziumOffset lowWatermark() {
        return lowWatermark;
    }

    public DebeziumOffset highWatermark() {
        return highWatermark;
    }

    public DebeziumOffset schemaHistoryWatermark() {
        return schemaHistoryWatermark;
    }

    public byte[] schemaHistory() {
        return schemaHistory.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HybridPendingSplitsState state)) return false;
        return isBinlogSplitAssigned == state.isBinlogSplitAssigned
                && snapshotPendingSplits.equals(state.snapshotPendingSplits)
                && Objects.equals(pendingBinlog, state.pendingBinlog)
                && phase == state.phase
                && Objects.equals(lowWatermark, state.lowWatermark)
                && Objects.equals(highWatermark, state.highWatermark)
                && Objects.equals(schemaHistoryWatermark, state.schemaHistoryWatermark)
                && Arrays.equals(schemaHistory, state.schemaHistory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                snapshotPendingSplits,
                isBinlogSplitAssigned,
                pendingBinlog,
                phase,
                lowWatermark,
                highWatermark,
                schemaHistoryWatermark,
                Arrays.hashCode(schemaHistory));
    }

    @Override
    public String toString() {
        return "HybridPendingSplitsState{phase=" + phase
                + ", chunks=" + snapshotPendingSplits.totalSnapshotSplits()
                + ", assignerStatus=" + snapshotPendingSplits.getSnapshotAssignerStatus()
                + ", checkpoint=redacted}";
    }
}
