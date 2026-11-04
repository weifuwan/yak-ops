package org.apache.flink.cdc.connectors.base.source.assigner.state;

import java.util.Objects;

/** A {@link PendingSplitsState} for pending hybrid (snapshot & stream) splits. */
public class HybridPendingSplitsState extends PendingSplitsState {
    private final SnapshotPendingSplitsState snapshotPendingSplits;
    private final boolean isStreamSplitAssigned;

    public HybridPendingSplitsState(
            SnapshotPendingSplitsState snapshotPendingSplits, boolean isStreamSplitAssigned) {
        this.snapshotPendingSplits = snapshotPendingSplits;
        this.isStreamSplitAssigned = isStreamSplitAssigned;
    }

    public SnapshotPendingSplitsState getSnapshotPendingSplits() {
        return snapshotPendingSplits;
    }

    public boolean isStreamSplitAssigned() {
        return isStreamSplitAssigned;
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
        return isStreamSplitAssigned == that.isStreamSplitAssigned
                && Objects.equals(snapshotPendingSplits, that.snapshotPendingSplits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotPendingSplits, isStreamSplitAssigned);
    }

    @Override
    public String toString() {
        return "HybridPendingSplitsState{"
                + "snapshotPendingSplits="
                + snapshotPendingSplits
                + ", isStreamSplitAssigned="
                + isStreamSplitAssigned
                + '}';
    }
}
