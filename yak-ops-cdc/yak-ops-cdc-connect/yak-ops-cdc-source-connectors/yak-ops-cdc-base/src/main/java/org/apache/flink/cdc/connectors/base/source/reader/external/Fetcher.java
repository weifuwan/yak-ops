package org.apache.flink.cdc.connectors.base.source.reader.external;

import org.apache.flink.cdc.common.annotation.Experimental;
import org.apache.flink.cdc.connectors.base.source.meta.split.SnapshotSplit;
import org.apache.flink.cdc.connectors.base.source.meta.split.StreamSplit;

import javax.annotation.Nullable;

import java.util.Iterator;

/**
 * Fetcher to fetch data of a table split, the split is either snapshot split {@link SnapshotSplit}
 * or stream split {@link StreamSplit}.
 */
@Experimental
public interface Fetcher<T, Split> {

    /** Add to task to fetch, this should call only when the reader is idle. */
    void submitTask(FetchTask<Split> fetchTask);

    /**
     * Fetched records from data source. The method should return null when reaching the end of the
     * split, the empty {@link Iterator} will be returned if the data of split is on pulling.
     */
    @Nullable
    Iterator<T> pollSplitRecords() throws InterruptedException;

    /** Return the current fetch task is finished or not. */
    boolean isFinished();

    /** Close the client and releases all resources. */
    void close();
}
