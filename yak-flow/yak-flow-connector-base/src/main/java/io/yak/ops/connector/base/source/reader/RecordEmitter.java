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

    void emitRecord(E record, ReaderOutput<T> output, StateT splitState) throws Exception;
}
