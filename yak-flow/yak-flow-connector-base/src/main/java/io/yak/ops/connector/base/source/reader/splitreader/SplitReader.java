package io.yak.ops.connector.base.source.reader.splitreader;

import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.List;

/**
 * Connector-specific blocking I/O running exclusively on one fetcher thread.
 *
 * <p>The mailbox may call wakeUp() concurrently to unblock fetch() during cancellation or new
 * split delivery. A fetch should return a bounded batch instead of materializing an entire split.
 */
public interface SplitReader<E, SplitT extends SourceSplit> extends AutoCloseable {

    /** Adds assigned work on the fetcher thread, not on the mailbox thread. */
    void addSplits(List<SplitT> splits) throws Exception;

    RecordsWithSplitIds<E> fetch() throws Exception;

    /** Wakes an in-flight blocking fetch without making the reader unusable. */
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
