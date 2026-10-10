package io.yak.ops.connector.base.source.reader;

import java.util.Set;

/**
 * One bounded fetch batch grouped by split ID, with completion markers ordered after its records.
 *
 * <p>Call nextSplit() before nextRecordFromSplit(). A null record means the current split has no
 * further records in this batch. A null split ID means the batch has been exhausted.
 */
public interface RecordsWithSplitIds<E> {

    /**
     * Advances to the next split represented in this bounded fetch batch.
     *
     * @return split ID, or null after all split groups are exhausted
     */
    String nextSplit();

    /**
     * Reads the next record from the split most recently selected by {@link #nextSplit()}.
     *
     * @return next fetched record, or null when the current split's records are exhausted
     */
    E nextRecordFromSplit();

    /**
     * Returns split IDs whose completion markers follow the records in this batch.
     *
     * <p>The mailbox must not mark a split finished until all prior records have been
     * emitted successfully.
     *
     * @return split IDs eligible for completion after record consumption
     */
    Set<String> finishedSplits();

    /** Releases resources belonging to a batch after the mailbox has consumed it. */
    default void recycle() {}
}
