package org.apache.flink.cdc.connectors.base.source.meta.split;

import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * An implementation of {@link RecordsWithSplitIds} which contains the records of one table split.
 */
public final class ChangeEventRecords implements RecordsWithSplitIds<SourceRecords> {

    @Nullable private String splitId;
    @Nullable private Iterator<SourceRecords> recordsForCurrentSplit;
    @Nullable private final Iterator<SourceRecords> recordsForSplit;
    private final Set<String> finishedSnapshotSplits;

    public ChangeEventRecords(
            @Nullable String splitId,
            @Nullable Iterator recordsForSplit,
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

    public static ChangeEventRecords forRecords(
            final String splitId, final Iterator<SourceRecords> recordsForSplit) {
        return new ChangeEventRecords(splitId, recordsForSplit, Collections.emptySet());
    }

    public static ChangeEventRecords forSnapshotRecords(
            final String splitId, final Iterator<SourceRecords> recordsForSplit) {
        return new ChangeEventRecords(splitId, recordsForSplit, Collections.singleton(splitId));
    }

    public static ChangeEventRecords forFinishedSplit(final String splitId) {
        return new ChangeEventRecords(null, null, Collections.singleton(splitId));
    }
}
