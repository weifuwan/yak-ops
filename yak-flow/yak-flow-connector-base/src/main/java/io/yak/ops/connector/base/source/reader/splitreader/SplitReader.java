package io.yak.ops.connector.base.source.reader.splitreader;

import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.List;

/**
 * Defines connector-specific blocking split I/O owned by one background fetcher thread.
 *
 * <p>The mailbox may signal {@link #wakeUp()} concurrently with an in-flight fetch, but
 * the fetcher's thread owns calls to addSplits, fetch and close. Fetch results must be
 * bounded so no thread materializes an entire large table split in memory.
 */
public interface SplitReader<E, SplitT extends SourceSplit> extends AutoCloseable {

    /** Adds assigned work on the fetcher thread, not on the mailbox thread. */
    void addSplits(List<SplitT> splits) throws Exception;

    /**
     * Fetches at most one bounded handover batch without updating mailbox checkpoint state.
     *
     * @return records grouped by split, with completion markers applied after emission
     * @throws Exception if blocking connector I/O or record conversion fails
     */
    RecordsWithSplitIds<E> fetch() throws Exception;

    /** Wakes an in-flight fetch for normal split assignment without terminal cancellation. */
    void wakeUp();

    /**
     * Stops active I/O when the owning fetcher is being shut down.
     *
     * <p>Unlike wakeUp(), this signal is not used for normal split assignment. A JDBC reader
     * may therefore cancel its active Statement without aborting a healthy running query.
     */
    default void cancel() {
        wakeUp();
    }

    @Override
    void close() throws Exception;
}
