package io.yak.ops.connector.base.source.reader.fetcher;

import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.connector.base.source.reader.synchronization.FutureCompletingBlockingQueue;
import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.Objects;
import java.util.Set;

/**
 * Transfers one bounded fetch batch to the mailbox handover without losing it on queue wakeup.
 *
 * <p>Only the fetcher thread invokes run(). wakeUp() may be invoked from another thread.
 */
final class FetchTask<E, SplitT extends SourceSplit> implements SplitFetcherTask {

    private final int fetcherId;
    private final SplitReader<E, SplitT> splitReader;
    private final FutureCompletingBlockingQueue<RecordsWithSplitIds<E>> queue;
    private RecordsWithSplitIds<E> pendingBatch;
    private Set<String> completedSplits = Set.of();

    FetchTask(
            int fetcherId,
            SplitReader<E, SplitT> splitReader,
            FutureCompletingBlockingQueue<RecordsWithSplitIds<E>> queue) {
        this.fetcherId = fetcherId;
        this.splitReader = Objects.requireNonNull(splitReader, "splitReader");
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    @Override
    public boolean run() throws Exception {
        if (pendingBatch == null) {
            pendingBatch = Objects.requireNonNull(splitReader.fetch(), "SplitReader returned null batch");
        }
        if (!queue.put(fetcherId, pendingBatch)) {
            return false;
        }
        completedSplits = Set.copyOf(pendingBatch.finishedSplits());
        pendingBatch = null;
        return true;
    }

    /** Split completion is reported only after the batch was successfully handed over. */
    Set<String> completedSplits() {
        return completedSplits;
    }

    @Override
    public void wakeUp() {
        // Notify both possible blocking points; a wakeup may race between fetch and put.
        splitReader.wakeUp();
        queue.wakeUpPuttingThread(fetcherId);
    }

    /** Discards an undelivered batch when the entire source is cancelled or failed. */
    void discard() {
        if (pendingBatch != null) {
            pendingBatch.recycle();
            pendingBatch = null;
        }
    }
}
