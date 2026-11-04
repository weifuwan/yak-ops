package org.apache.flink.cdc.connectors.base.source.meta.split;

import org.apache.flink.cdc.connectors.base.source.meta.offset.Offset;

import io.debezium.relational.TableId;
import io.debezium.relational.history.TableChanges.TableChange;

import javax.annotation.Nullable;

import java.util.Map;

/** The state of split to describe the change log of table(s). */
public class StreamSplitState extends SourceSplitState {

    @Nullable private Offset startingOffset;
    @Nullable private Offset endingOffset;
    private final Map<TableId, TableChange> tableSchemas;

    public StreamSplitState(StreamSplit split) {
        super(split);
        this.startingOffset = split.getStartingOffset();
        this.endingOffset = split.getEndingOffset();
        this.tableSchemas = split.getTableSchemas();
    }

    @Nullable
    public Offset getStartingOffset() {
        return startingOffset;
    }

    public void setStartingOffset(@Nullable Offset startingOffset) {
        this.startingOffset = startingOffset;
    }

    @Nullable
    public Offset getEndingOffset() {
        return endingOffset;
    }

    public void setEndingOffset(@Nullable Offset endingOffset) {
        this.endingOffset = endingOffset;
    }

    public Map<TableId, TableChange> getTableSchemas() {
        return tableSchemas;
    }

    public void recordSchema(TableId tableId, TableChange latestTableChange) {
        this.tableSchemas.put(tableId, latestTableChange);
    }

    @Override
    public StreamSplit toSourceSplit() {
        final StreamSplit streamSplit = split.asStreamSplit();
        return new StreamSplit(
                streamSplit.splitId(),
                getStartingOffset(),
                getEndingOffset(),
                streamSplit.asStreamSplit().getFinishedSnapshotSplitInfos(),
                getTableSchemas(),
                streamSplit.getTotalFinishedSplitSize());
    }

    @Override
    public String toString() {
        return "StreamSplitState{"
                + "startingOffset="
                + startingOffset
                + ", endingOffset="
                + endingOffset
                + ", split="
                + split
                + '}';
    }
}
