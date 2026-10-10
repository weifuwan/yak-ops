package io.yak.ops.connector.base.source.reader;

import io.yak.ops.core.api.connector.source.ReaderOutput;

/**
 * Converts a fetched record into downstream output and advances the mailbox-owned split state.
 *
 * <p>The split state must be advanced only after the output has accepted the record. A fetcher's
 * prefetch position must never be mistaken for a checkpoint-safe consumption position.
 *
 * @param <E> fetched record with any connector-specific progress metadata
 * @param <T> downstream record
 * @param <StateT> mutable reader-local split state
 */
@FunctionalInterface
public interface RecordEmitter<E, T, StateT> {

    /**
     * Emits one prefetched record and only then advances its mutable split checkpoint state.
     *
     * <p>The method runs on the reader's task mailbox, not on a background fetcher thread.
     * A downstream failure must leave the split cursor unadvanced for recovery.
     *
     * @param record fetched record, including any source-specific cursor
     * @param output destination for the converted record
     * @param splitState mailbox-owned progress for the assigned split
     * @throws Exception if conversion, emission or cursor advancement fails
     */
    void emitRecord(E record, ReaderOutput<T> output, StateT splitState) throws Exception;
}
