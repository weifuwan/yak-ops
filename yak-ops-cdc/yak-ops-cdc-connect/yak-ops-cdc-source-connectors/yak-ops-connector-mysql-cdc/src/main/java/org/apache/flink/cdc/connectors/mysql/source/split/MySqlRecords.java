package org.apache.flink.cdc.connectors.mysql.source.split;

import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * An implementation of {@link RecordsWithSplitIds} which contains the records of one table split.
 */
public final class MySqlRecords implements RecordsWithSplitIds<SourceRecords> {

    @Nullable private String splitId;
    @Nullable private Iterator<SourceRecords> recordsForCurrentSplit;
    @Nullable private final Iterator<SourceRecords> recordsForSplit;
    private final Set<String> finishedSnapshotSplits;

    public MySqlRecords(
            @Nullable String splitId,
            @Nullable Iterator<SourceRecords> recordsForSplit,
            Set<String> finishedSnapshotSplits) {
        this.splitId = splitId;
        this.recordsForSplit = recordsForSplit;
        this.finishedSnapshotSplits = finishedSnapshotSplits;
    }

    @Nullable
    @Override
    public String nextSplit() {
        // move the split one (from current value to null)
        final String nextSplit = this.splitId;
        this.splitId = null;

        // move the iterator, from null to value (if first move) or to null (if second move)
        this.recordsForCurrentSplit = nextSplit != null ? this.recordsForSplit : null;
        return nextSplit;
    }

    @Nullable
    @Override
    public SourceRecords nextRecordFromSplit() {
        final Iterator<SourceRecords> recordsForSplit = this.recordsForCurrentSplit;
        if (recordsForSplit != null) {
            if (recordsForSplit.hasNext()) {
                return recordsForSplit.next();
            } else {
                return null;
            }
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public Set<String> finishedSplits() {
        return finishedSnapshotSplits;
    }

    public static MySqlRecords forUnfinishedRecords(
            final String splitId, final Iterator<SourceRecords> recordsForSplit) {
        return new MySqlRecords(splitId, recordsForSplit, Collections.emptySet());
    }

    public static MySqlRecords forFinishedSplit(final String splitId) {
        return new MySqlRecords(null, null, Collections.singleton(splitId));
    }
}
