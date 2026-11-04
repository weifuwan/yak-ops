package org.apache.flink.cdc.connectors.mysql.source.assigners.state;

import java.util.Objects;

/** A {@link PendingSplitsState} for pending binlog splits. */
public class BinlogPendingSplitsState extends PendingSplitsState {

    private final boolean isBinlogSplitAssigned;

    public BinlogPendingSplitsState(boolean isBinlogSplitAssigned) {
        this.isBinlogSplitAssigned = isBinlogSplitAssigned;
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
        BinlogPendingSplitsState that = (BinlogPendingSplitsState) o;
        return isBinlogSplitAssigned == that.isBinlogSplitAssigned;
    }

    @Override
    public int hashCode() {
        return Objects.hash(isBinlogSplitAssigned);
    }

    @Override
    public String toString() {
        return "BinlogPendingSplitsState{" + "isBinlogSplitAssigned=" + isBinlogSplitAssigned + '}';
    }
}
