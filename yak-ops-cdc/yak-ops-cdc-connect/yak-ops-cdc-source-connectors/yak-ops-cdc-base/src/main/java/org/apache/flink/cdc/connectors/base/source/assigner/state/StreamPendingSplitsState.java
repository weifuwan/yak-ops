package org.apache.flink.cdc.connectors.base.source.assigner.state;

import java.util.Objects;

/** A {@link PendingSplitsState} for pending stream splits. */
public class StreamPendingSplitsState extends PendingSplitsState {

    private final boolean isStreamSplitAssigned;

    public StreamPendingSplitsState(boolean isStreamSplitAssigned) {
        this.isStreamSplitAssigned = isStreamSplitAssigned;
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
        StreamPendingSplitsState that = (StreamPendingSplitsState) o;
        return isStreamSplitAssigned == that.isStreamSplitAssigned;
    }

    @Override
    public int hashCode() {
        return Objects.hash(isStreamSplitAssigned);
    }

    @Override
    public String toString() {
        return "StreamPendingSplitsState{" + "isStreamSplitAssigned=" + isStreamSplitAssigned + '}';
    }
}
