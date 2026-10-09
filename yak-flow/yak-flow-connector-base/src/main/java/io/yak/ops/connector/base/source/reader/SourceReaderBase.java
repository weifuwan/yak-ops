package io.yak.ops.connector.base.source.reader;

import io.yak.ops.connector.base.source.reader.fetcher.SplitFetcherManager;
import io.yak.ops.connector.base.source.reader.synchronization.FutureCompletingBlockingQueue;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Mailbox-owned consumption and checkpoint state for asynchronous SourceReader implementations.
 *
 * <p>The fetcher threads own blocking I/O and never mutate split checkpoint state. Only this
 * reader invokes RecordEmitter on the mailbox, thereby advancing progress after downstream output.
 *
 * @param <E> fetched record type
 * @param <T> emitted record type
 * @param <SplitT> checkpointed split type
 * @param <StateT> mutable reader-local split state
 */
public abstract class SourceReaderBase<E, T, SplitT extends SourceSplit, StateT>
        implements SourceReader<T, SplitT> {

    protected final SourceReaderContext context;
    private final SplitFetcherManager<E, SplitT> fetchers;
    private final RecordEmitter<E, T, StateT> emitter;
    private final FutureCompletingBlockingQueue<RecordsWithSplitIds<E>> queue;
    private final Map<String, StateT> splitStates = new LinkedHashMap<>();
    private final long closeTimeoutMillis;

    private RecordsWithSplitIds<E> currentFetch;
    private String currentSplitId;
    private boolean noMoreSplits;
    private boolean closed;

    protected SourceReaderBase(
            SplitFetcherManager<E, SplitT> fetchers,
            RecordEmitter<E, T, StateT> emitter,
            SourceReaderContext context) {
        this.fetchers = Objects.requireNonNull(fetchers, "fetchers");
        this.emitter = Objects.requireNonNull(emitter, "emitter");
        this.context = Objects.requireNonNull(context, "context");
        this.queue = fetchers.getQueue();
        this.closeTimeoutMillis = context.getConfiguration().get(SourceReaderOptions.CLOSE_TIMEOUT_MILLIS);
        if (closeTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Source reader close timeout must be positive");
        }
    }

    @Override
    public void start() throws Exception {}

    @Override
    public InputStatus pollNext(ReaderOutput<T> output) throws Exception {
        Objects.requireNonNull(output, "output");
        ensureOpen();
        fetchers.checkErrors();
        while (true) {
            if (currentFetch == null) {
                currentFetch = queue.poll();
                if (currentFetch == null) {
                    return finishedOrAvailableLater();
                }
            }
            if (currentSplitId == null) {
                currentSplitId = currentFetch.nextSplit();
                if (currentSplitId == null) {
                    finishCurrentFetch();
                    continue;
                }
            }
            E record = currentFetch.nextRecordFromSplit();
            if (record != null) {
                StateT state = splitStates.get(currentSplitId);
                if (state == null) {
                    throw new IllegalStateException("Fetched record for unregistered split: " + currentSplitId);
                }
                emitter.emitRecord(record, output, state);
                return InputStatus.MORE_AVAILABLE;
            }
            currentSplitId = null;
        }
    }

    @Override
    public CompletableFuture<Void> isAvailable() {
        ensureOpen();
        return currentFetch == null ? queue.getAvailabilityFuture() : CompletableFuture.completedFuture(null);
    }

    @Override
    public void addSplits(List<SplitT> splits) throws Exception {
        ensureOpen();
        if (noMoreSplits) {
            throw new IllegalStateException("Cannot assign splits after no-more-splits");
        }
        List<SplitT> added = List.copyOf(Objects.requireNonNull(splits, "splits"));
        if (added.isEmpty()) {
            return;
        }
        for (SplitT split : added) {
            String id = Objects.requireNonNull(split.splitId(), "splitId");
            if (id.isBlank() || splitStates.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate or blank split ID: " + id);
            }
        }
        for (SplitT split : added) {
            splitStates.put(split.splitId(), initializedState(split));
        }
        fetchers.addSplits(added);
    }

    @Override
    public void notifyNoMoreSplits() {
        ensureOpen();
        if (noMoreSplits) {
            throw new IllegalStateException("No-more-splits already signalled");
        }
        noMoreSplits = true;
        queue.notifyAvailable();
    }

    @Override
    public List<SplitT> snapshotState(long checkpointId) {
        ensureOpen();
        if (checkpointId < 0) {
            throw new IllegalArgumentException("Checkpoint ID must be nonnegative");
        }
        List<SplitT> state = new ArrayList<>(splitStates.size());
        splitStates.forEach((id, value) -> state.add(toSplitType(id, value)));
        return List.copyOf(state);
    }

    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        try {
            fetchers.close(closeTimeoutMillis);
        } finally {
            if (currentFetch != null) {
                currentFetch.recycle();
                currentFetch = null;
            }
            RecordsWithSplitIds<E> batch;
            while ((batch = queue.poll()) != null) {
                batch.recycle();
            }
            splitStates.clear();
        }
    }

    /** Initializes the mailbox-owned mutable state of an assigned or restored split. */
    protected abstract StateT initializedState(SplitT split);

    /** Returns a detached, checkpointable split containing the last consumed position. */
    protected abstract SplitT toSplitType(String splitId, StateT state);

    /** Called after all records preceding a split's completion marker have been consumed. */
    protected abstract void onSplitFinished(Map<String, StateT> finished);

    private void finishCurrentFetch() {
        Map<String, StateT> finished = new LinkedHashMap<>();
        for (String id : currentFetch.finishedSplits()) {
            StateT state = splitStates.remove(id);
            if (state == null) {
                throw new IllegalStateException("Finished unknown split: " + id);
            }
            finished.put(id, state);
        }
        currentFetch.recycle();
        currentFetch = null;
        currentSplitId = null;
        if (!finished.isEmpty()) {
            onSplitFinished(Map.copyOf(finished));
        }
    }

    private InputStatus finishedOrAvailableLater() {
        fetchers.checkErrors();
        if (!noMoreSplits) {
            return InputStatus.NOTHING_AVAILABLE;
        }
        if (!queue.isEmpty()) {
            return InputStatus.MORE_AVAILABLE;
        }
        fetchers.shutdownIdleFetchers();
        fetchers.checkErrors();
        return fetchers.hasAliveFetchers() ? InputStatus.NOTHING_AVAILABLE : InputStatus.END_OF_INPUT;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Source reader already closed");
        }
    }
}
