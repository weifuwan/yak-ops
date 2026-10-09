package io.yak.ops.connector.base.source.reader;

import java.util.Set;

/**
 * One bounded fetch batch grouped by split ID, with completion markers ordered after its records.
 *
 * <p>Call nextSplit() before nextRecordFromSplit(). A null record means the current split has no
 * further records in this batch. A null split ID means the batch has been exhausted.
 */
public interface RecordsWithSplitIds<E> {

    String nextSplit();

    E nextRecordFromSplit();

    Set<String> finishedSplits();

    /** Releases resources belonging to a batch after the mailbox has consumed it. */
    default void recycle() {}
}
