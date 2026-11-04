package org.apache.flink.cdc.connectors.base.source.meta.split;

import org.apache.flink.cdc.connectors.base.source.meta.offset.Offset;

import javax.annotation.Nullable;

/** The state of split to describe the snapshot of table(s). */
public class SnapshotSplitState extends SourceSplitState {

    @Nullable private Offset highWatermark;

    public SnapshotSplitState(SnapshotSplit split) {
        super(split);
        this.highWatermark = split.getHighWatermark();
    }

    @Nullable
    public Offset getHighWatermark() {
        return highWatermark;
    }

    public void setHighWatermark(@Nullable Offset highWatermark) {
        this.highWatermark = highWatermark;
    }

    @Override
    public SnapshotSplit toSourceSplit() {
        final SnapshotSplit snapshotSplit = split.asSnapshotSplit();
        return new SnapshotSplit(
                snapshotSplit.asSnapshotSplit().getTableId(),
                snapshotSplit.splitId(),
                snapshotSplit.getSplitKeyType(),
                snapshotSplit.getSplitStart(),
                snapshotSplit.getSplitEnd(),
                getHighWatermark(),
                snapshotSplit.getTableSchemas());
    }

    @Override
    public String toString() {
        return "SnapshotSplitState{highWatermark=" + highWatermark + ", split=" + split + '}';
    }
}
