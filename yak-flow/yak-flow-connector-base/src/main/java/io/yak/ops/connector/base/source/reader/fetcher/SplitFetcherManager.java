package io.yak.ops.connector.base.source.reader.fetcher;

import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.base.source.reader.SourceReaderOptions;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.connector.base.source.reader.synchronization.FutureCompletingBlockingQueue;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.configuration.Configuration;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Owns source fetcher threads, their shared bounded handover and asynchronous error reporting.
 *
 * <p>Job failure and restart belong to Runtime; this manager only propagates fetcher errors to
 * SourceReader.pollNext() and releases the resources owned by its reader.
 */
public abstract class SplitFetcherManager<E, SplitT extends SourceSplit> {

    private final Supplier<? extends SplitReader<E, SplitT>> splitReaderFactory;
    private final FutureCompletingBlockingQueue<RecordsWithSplitIds<E>> queue;
    private final ConcurrentHashMap<Integer, SplitFetcher<E, SplitT>> fetchers = new ConcurrentHashMap<>();
    private final AtomicReference<Throwable> fetcherError = new AtomicReference<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private final ExecutorService workers = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("yak-source-fetcher-", 0).factory());

    private volatile boolean closed;

    protected SplitFetcherManager(
            Supplier<? extends SplitReader<E, SplitT>> splitReaderFactory, Configuration configuration) {
        this.splitReaderFactory = Objects.requireNonNull(splitReaderFactory, "splitReaderFactory");
        int capacity =
                Objects.requireNonNull(configuration, "configuration").get(SourceReaderOptions.ELEMENT_QUEUE_CAPACITY);
        this.queue = new FutureCompletingBlockingQueue<>(capacity);
    }

    /** Distributes assigned splits to one or more fetchers without doing blocking I/O. */
    public abstract void addSplits(List<SplitT> splits);

    public final FutureCompletingBlockingQueue<RecordsWithSplitIds<E>> getQueue() {
        return queue;
    }

    /** Propagates background failure on the mailbox thread. */
    public final void checkErrors() {
        Throwable cause = fetcherError.get();
        if (cause != null) {
            throw new IllegalStateException("Source fetcher failed", cause);
        }
    }

    /** Shuts down only fetchers that have no work left; called after queued records are drained. */
    public final void shutdownIdleFetchers() {
        fetchers.values().forEach(fetcher -> {
            if (fetcher.isIdle()) {
                fetcher.shutdown();
            }
        });
    }

    public final boolean hasAliveFetchers() {
        return !fetchers.isEmpty();
    }

    /** Cancels all fetchers and waits up to the configured bound for resource release. */
    public final synchronized void close(long timeoutMillis) throws Exception {
        if (closed) {
            return;
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Close timeout must be positive");
        }
        closed = true;
        fetchers.values().forEach(SplitFetcher::shutdown);
        workers.shutdown();
        try {
            if (!workers.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                workers.shutdownNow();
                throw new IOException("Source fetchers did not stop within the close timeout");
            }
        } catch (InterruptedException interruption) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
            throw interruption;
        }
    }

    protected final synchronized SplitFetcher<E, SplitT> createFetcher() {
        if (closed) {
            throw new IllegalStateException("Fetcher manager already closed");
        }
        int id = nextId.getAndIncrement();
        SplitFetcher<E, SplitT> fetcher = new SplitFetcher<>(
                id,
                Objects.requireNonNull(splitReaderFactory.get(), "splitReader"),
                queue,
                cause -> {
                    if (!fetcherError.compareAndSet(null, cause)) {
                        fetcherError.get().addSuppressed(cause);
                    }
                    queue.notifyAvailable();
                },
                () -> {
                    fetchers.remove(id);
                    queue.releaseProducer(id);
                    queue.notifyAvailable();
                });
        fetchers.put(id, fetcher);
        return fetcher;
    }

    protected final void startFetcher(SplitFetcher<E, SplitT> fetcher) {
        workers.execute(fetcher);
    }

    protected final SplitFetcher<E, SplitT> getAnyFetcher() {
        return fetchers.values().stream().findFirst().orElse(null);
    }
}
