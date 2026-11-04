package org.apache.flink.cdc.connectors.base.source.meta.split;

/** State of the reader, essentially a mutable version of the {@link SourceSplitBase}. */
public abstract class SourceSplitState {

    protected final SourceSplitBase split;

    public SourceSplitState(SourceSplitBase split) {
        this.split = split;
    }

    /** Checks whether this split state is a snapshot split state. */
    public final boolean isSnapshotSplitState() {
        return getClass() == SnapshotSplitState.class;
    }

    /** Checks whether this split state is a stream split state. */
    public final boolean isStreamSplitState() {
        return getClass() == StreamSplitState.class;
    }

    /** Casts this split state into a {@link SnapshotSplitState}. */
    public final SnapshotSplitState asSnapshotSplitState() {
        return (SnapshotSplitState) this;
    }

    /** Casts this split state into a {@link StreamSplitState}. */
    public final StreamSplitState asStreamSplitState() {
        return (StreamSplitState) this;
    }

    /** Use the current split state to create a new SourceSplit. */
    public abstract SourceSplitBase toSourceSplit();
}
