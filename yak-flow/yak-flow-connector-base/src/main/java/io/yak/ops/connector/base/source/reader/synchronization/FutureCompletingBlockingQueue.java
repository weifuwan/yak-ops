package io.yak.ops.connector.base.source.reader.synchronization;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A bounded producer-blocking, consumer-nonblocking queue with mailbox availability signals.
 *
 * <p>A completed availability future is only a hint: errors, shutdown and empty completion
 * markers may wake a consumer without providing a record. Always poll after a wakeup.
 */
public final class FutureCompletingBlockingQueue<T> {

    private static final CompletableFuture<Void> AVAILABLE = CompletableFuture.completedFuture(null);

    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Queue<T> elements = new ArrayDeque<>();
    private final Set<Integer> wokenProducers = new HashSet<>();
    private CompletableFuture<Void> availability = new CompletableFuture<>();

    public FutureCompletingBlockingQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        this.capacity = capacity;
    }

    /**
     * Adds a batch, blocking only the producer when full.
     *
     * @return false when this producer was woken to handle a control event instead of enqueuing
     */
    public boolean put(int producerId, T element) throws InterruptedException {
        Objects.requireNonNull(element, "element");
        lock.lockInterruptibly();
        try {
            while (elements.size() >= capacity) {
                if (wokenProducers.remove(producerId)) {
                    return false;
                }
                notFull.await();
            }
            if (wokenProducers.remove(producerId)) {
                return false;
            }
            elements.add(element);
            signalAvailable();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Returns null immediately when no element is available. */
    public T poll() {
        lock.lock();
        try {
            T element = elements.poll();
            if (element != null) {
                notFull.signalAll();
            }
            if (elements.isEmpty() && availability.isDone()) {
                availability = new CompletableFuture<>();
            }
            return element;
        } finally {
            lock.unlock();
        }
    }

    /** Returns a future that is completed when data or a control notification becomes available. */
    public CompletableFuture<Void> getAvailabilityFuture() {
        lock.lock();
        try {
            return availability;
        } finally {
            lock.unlock();
        }
    }

    /** Wakes the mailbox for data-independent events such as failure and fetcher termination. */
    public void notifyAvailable() {
        lock.lock();
        try {
            signalAvailable();
        } finally {
            lock.unlock();
        }
    }

    /** Interrupts a blocked put; the request remains visible if it races with the put. */
    public void wakeUpPuttingThread(int producerId) {
        lock.lock();
        try {
            wokenProducers.add(producerId);
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Clears a producer's wakeup state only after its thread has terminated. */
    public void releaseProducer(int producerId) {
        lock.lock();
        try {
            wokenProducers.remove(producerId);
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        lock.lock();
        try {
            return elements.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return elements.size();
        } finally {
            lock.unlock();
        }
    }

    private void signalAvailable() {
        CompletableFuture<Void> old = availability;
        availability = AVAILABLE;
        if (!old.isDone()) {
            old.complete(null);
        }
    }
}
