package org.apache.flink.cdc.connectors.mysql.source.split;

/** State of the reader, essentially a mutable version of the {@link MySqlSplit}. */
public abstract class MySqlSplitState {

    protected final MySqlSplit split;

    public MySqlSplitState(MySqlSplit split) {
        this.split = split;
    }

    /** Checks whether this split state is a snapshot split state. */
    public final boolean isSnapshotSplitState() {
        return getClass() == MySqlSnapshotSplitState.class;
    }

    /** Checks whether this split state is a binlog split state. */
    public final boolean isBinlogSplitState() {
        return getClass() == MySqlBinlogSplitState.class;
    }

    /** Casts this split state into a {@link MySqlSnapshotSplitState}. */
    public final MySqlSnapshotSplitState asSnapshotSplitState() {
        return (MySqlSnapshotSplitState) this;
    }

    /** Casts this split state into a {@link MySqlBinlogSplitState}. */
    public final MySqlBinlogSplitState asBinlogSplitState() {
        return (MySqlBinlogSplitState) this;
    }

    /** Use the current split state to create a new MySqlSplit. */
    public abstract MySqlSplit toMySqlSplit();
}
