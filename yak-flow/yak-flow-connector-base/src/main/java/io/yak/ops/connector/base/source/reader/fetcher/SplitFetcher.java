package io.yak.ops.connector.base.source.reader.fetcher;

import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.connector.base.source.reader.synchronization.FutureCompletingBlockingQueue;
import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Runs split-control actions and blocking fetches serially on a dedicated background thread.
 *
 * <p>Mailbox threads enqueue assignments and wake a fetch; they never access SplitReader's
 * mutable JDBC or CDC I/O state directly.
 */
final class SplitFetcher<E, SplitT extends SourceSplit> implements Runnable {

    private final int id;
    private final SplitReader<E, SplitT> splitReader;
    private final FetchTask<E, SplitT> fetchTask;
    private final Consumer<Throwable> errorHandler;
    private final Runnable terminationHook;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition hasWork = lock.newCondition();
    private final Deque<SplitFetcherTask> tasks = new ArrayDeque<>();
    private final Map<String, SplitT> assignedSplits = new LinkedHashMap<>();

    private SplitFetcherTask runningTask;
    private boolean stopping;

    SplitFetcher(
            int id,
            SplitReader<E, SplitT> splitReader,
            FutureCompletingBlockingQueue<RecordsWithSplitIds<E>> queue,
            Consumer<Throwable> errorHandler,
            Runnable terminationHook) {
        this.id = id;
        this.splitReader = Objects.requireNonNull(splitReader, "splitReader");
        this.fetchTask = new FetchTask<>(id, splitReader, queue);
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        this.terminationHook = Objects.requireNonNull(terminationHook, "terminationHook");
    }

    void addSplits(List<SplitT> splits) {
        SplitFetcherTask inFlight;
        lock.lock();
        try {
            if (stopping) {
                throw new IllegalStateException("Cannot add splits to a stopping fetcher");
            }
            Set<String> seen = new HashSet<>();
            for (SplitT split : splits) {
                String splitId = Objects.requireNonNull(split.splitId(), "splitId");
                if (splitId.isBlank() || !seen.add(splitId) || assignedSplits.containsKey(splitId)) {
                    throw new IllegalArgumentException("Duplicate or blank split ID: " + splitId);
                }
            }
            splits.forEach(split -> assignedSplits.put(split.splitId(), split));
            tasks.addLast(new AddSplitsTask<>(splitReader, splits));
            hasWork.signal();
            inFlight = runningTask;
        } finally {
            lock.unlock();
        }
        if (inFlight == fetchTask) {
            fetchTask.wakeUp();
        }
    }

    boolean isIdle() {
        lock.lock();
        try {
            return assignedSplits.isEmpty() && tasks.isEmpty() && runningTask == null;
        } finally {
            lock.unlock();
        }
    }

    void shutdown() {
        SplitFetcherTask inFlight;
        lock.lock();
        try {
            if (stopping) {
                return;
            }
            stopping = true;
            hasWork.signalAll();
            inFlight = runningTask;
        } finally {
            lock.unlock();
        }
        if (inFlight != null) {
            inFlight.wakeUp();
        }
    }

    @Override
    public void run() {
        Throwable failure = null;
        try {
            while (true) {
                SplitFetcherTask task = nextTask();
                if (task == null) {
                    break;
                }
                boolean complete = task.run();
                lock.lock();
                try {
                    runningTask = null;
                    if (!complete && task != fetchTask) {
                        tasks.addFirst(task);
                    }
                    if (complete && task == fetchTask) {
                        fetchTask.completedSplits().forEach(assignedSplits::remove);
                    }
                } finally {
                    lock.unlock();
                }
            }
        } catch (Throwable exception) {
            failure = exception;
        } finally {
            fetchTask.discard();
            try {
                splitReader.close();
            } catch (Throwable closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            } finally {
                if (failure != null) {
                    errorHandler.accept(failure);
                }
                terminationHook.run();
            }
        }
    }

    private SplitFetcherTask nextTask() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (true) {
                if (stopping) {
                    return null;
                }
                if (!tasks.isEmpty()) {
                    runningTask = tasks.removeFirst();
                    return runningTask;
                }
                if (!assignedSplits.isEmpty()) {
                    runningTask = fetchTask;
                    return runningTask;
                }
                hasWork.await();
            }
        } finally {
            lock.unlock();
        }
    }
}
