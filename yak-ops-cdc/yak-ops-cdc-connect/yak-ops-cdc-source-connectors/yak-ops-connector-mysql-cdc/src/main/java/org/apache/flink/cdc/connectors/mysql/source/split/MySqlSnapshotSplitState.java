package org.apache.flink.cdc.connectors.mysql.source.split;

import org.apache.flink.cdc.connectors.mysql.source.offset.BinlogOffset;

import javax.annotation.Nullable;

/** The state of split to describe the binlog of MySql table(s). */
public class MySqlSnapshotSplitState extends MySqlSplitState {

    @Nullable private BinlogOffset highWatermark;

    public MySqlSnapshotSplitState(MySqlSnapshotSplit split) {
        super(split);
        this.highWatermark = split.getHighWatermark();
    }

    @Nullable
    public BinlogOffset getHighWatermark() {
        return highWatermark;
    }

    public void setHighWatermark(@Nullable BinlogOffset highWatermark) {
        this.highWatermark = highWatermark;
    }

    public MySqlSnapshotSplit toMySqlSplit() {
        final MySqlSnapshotSplit snapshotSplit = split.asSnapshotSplit();
        return new MySqlSnapshotSplit(
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
        return "MySqlSnapshotSplitState{"
                + "highWatermark="
                + highWatermark
                + ", split="
                + split
                + '}';
    }
}
