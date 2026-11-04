package org.apache.flink.cdc.connectors.mysql.source.assigners.state;

import java.util.Objects;

/** A {@link PendingSplitsState} for pending hybrid (snapshot & binlog) splits. */
public class HybridPendingSplitsState extends PendingSplitsState {
    private final SnapshotPendingSplitsState snapshotPendingSplits;
    private final boolean isBinlogSplitAssigned;

    public HybridPendingSplitsState(
            SnapshotPendingSplitsState snapshotPendingSplits, boolean isBinlogSplitAssigned) {
        this.snapshotPendingSplits = snapshotPendingSplits;
        this.isBinlogSplitAssigned = isBinlogSplitAssigned;
    }

    public SnapshotPendingSplitsState getSnapshotPendingSplits() {
        return snapshotPendingSplits;
    }

    public boolean isBinlogSplitAssigned() {
        return isBinlogSplitAssigned;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HybridPendingSplitsState that = (HybridPendingSplitsState) o;
        return isBinlogSplitAssigned == that.isBinlogSplitAssigned
                && Objects.equals(snapshotPendingSplits, that.snapshotPendingSplits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotPendingSplits, isBinlogSplitAssigned);
    }

    @Override
    public String toString() {
        return "HybridPendingSplitsState{"
                + "snapshotPendingSplits="
                + snapshotPendingSplits
                + ", isBinlogSplitAssigned="
                + isBinlogSplitAssigned
                + '}';
    }
}
