package org.apache.flink.cdc.connectors.mysql.source.split;

import org.apache.flink.cdc.connectors.mysql.source.offset.BinlogOffset;

import io.debezium.relational.TableId;
import io.debezium.relational.history.TableChanges.TableChange;

import javax.annotation.Nullable;

import java.util.Map;

/** The state of split to describe the binlog of MySql table(s). */
public class MySqlBinlogSplitState extends MySqlSplitState {

    @Nullable private BinlogOffset startingOffset;
    @Nullable private BinlogOffset endingOffset;
    private final Map<TableId, TableChange> tableSchemas;

    public MySqlBinlogSplitState(MySqlBinlogSplit split) {
        super(split);
        this.startingOffset = split.getStartingOffset();
        this.endingOffset = split.getEndingOffset();
        this.tableSchemas = split.getTableSchemas();
    }

    @Nullable
    public BinlogOffset getStartingOffset() {
        return startingOffset;
    }

    public void setStartingOffset(@Nullable BinlogOffset startingOffset) {
        this.startingOffset = startingOffset;
    }

    @Nullable
    public BinlogOffset getEndingOffset() {
        return endingOffset;
    }

    public void setEndingOffset(@Nullable BinlogOffset endingOffset) {
        this.endingOffset = endingOffset;
    }

    public Map<TableId, TableChange> getTableSchemas() {
        return tableSchemas;
    }

    public void recordSchema(TableId tableId, TableChange latestTableChange) {
        this.tableSchemas.put(tableId, latestTableChange);
    }

    public MySqlBinlogSplit toMySqlSplit() {
        final MySqlBinlogSplit binlogSplit = split.asBinlogSplit();
        return new MySqlBinlogSplit(
                binlogSplit.splitId(),
                getStartingOffset(),
                getEndingOffset(),
                binlogSplit.asBinlogSplit().getFinishedSnapshotSplitInfos(),
                getTableSchemas(),
                binlogSplit.getTotalFinishedSplitSize(),
                binlogSplit.isSuspended());
    }

    @Override
    public String toString() {
        return "MySqlBinlogSplitState{"
                + "startingOffset="
                + startingOffset
                + ", endingOffset="
                + endingOffset
                + ", split="
                + split
                + '}';
    }
}
