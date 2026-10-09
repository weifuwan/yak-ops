package io.yak.ops.flow.runtime.io.partition;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Local consumer-side input gate for one downstream subtask.
 *
 * <p>Each producer has a distinct ResultSubpartition. The gate arbitrates a single bounded buffer
 * budget shared by all producers, and delivers available records round-robin on the consumer Task
 * thread. Producers block on a condition, not a timed polling loop.
 */
public final class InputGate<T> {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition spaceAvailable = lock.newCondition();
    private final List<ResultSubpartition<T>> subpartitions;
    private final boolean[] producerFinished;
    private final int capacity;

    private int queuedRecords;
    private int processingRecords;
    private int remainingProducers;
    private int nextInput;
    private Throwable failure;
    private CompletableFuture<Void> available = new CompletableFuture<>();
    private CompletableFuture<Void> drained = CompletableFuture.completedFuture(null);

    public InputGate(int capacity, int producerCount) {
        if (capacity <= 0 || producerCount <= 0) {
            throw new IllegalArgumentException("InputGate capacity and producer count must be positive");
        }
        this.capacity = capacity;
        this.producerFinished = new boolean[producerCount];
        this.remainingProducers = producerCount;
        List<ResultSubpartition<T>> channels = new ArrayList<>(producerCount);
        for (int i = 0; i < producerCount; i++) {
            channels.add(new ResultSubpartition<>(this, i));
        }
        this.subpartitions = List.copyOf(channels);
    }

    /** Return the physical input channel for exactly one upstream producer. */
    public ResultSubpartition<T> getSubpartition(int producerIndex) {
        return subpartitions.get(producerIndex);
    }

    public int getNumberOfInputChannels() {
        return subpartitions.size();
    }

    void enqueue(ResultSubpartition<T> subpartition, T record) throws Exception {
        Objects.requireNonNull(record, "record");
        int producer = validateSubpartition(subpartition);
        lock.lockInterruptibly();
        try {
            checkFailure();
            if (producerFinished[producer]) {
                throw new IllegalStateException("Producer has already finished: " + producer);
            }
            while (queuedRecords == capacity) {
                spaceAvailable.await();
                checkFailure();
                if (producerFinished[producer]) {
                    throw new IllegalStateException("Producer has already finished: " + producer);
                }
            }
            subpartition.records.addLast(record);
            queuedRecords++;
            if (drained.isDone()) {
                drained = new CompletableFuture<>();
            }
            available.complete(null);
        } finally {
            lock.unlock();
        }
    }

    void finishProducer(ResultSubpartition<T> subpartition) {
        int producer = validateSubpartition(subpartition);
        lock.lock();
        try {
            if (failure != null) {
                throw new IllegalStateException("InputGate already failed", failure);
            }
            if (producerFinished[producer]) {
                throw new IllegalStateException("Producer finished twice: " + producer);
            }
            producerFinished[producer] = true;
            if (--remainingProducers == 0) {
                available.complete(null);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Process one record without blocking the downstream Task mailbox. */
    public InputStatus emitNext(ReaderOutput<T> output) throws Exception {
        Objects.requireNonNull(output, "output");
        T record = null;
        lock.lock();
        try {
            checkFailure();
            for (int i = 0; i < subpartitions.size(); i++) {
                int channel = (nextInput + i) % subpartitions.size();
                record = subpartitions.get(channel).records.pollFirst();
                if (record != null) {
                    nextInput = (channel + 1) % subpartitions.size();
                    queuedRecords--;
                    processingRecords++;
                    spaceAvailable.signal();
                    break;
                }
            }
            if (record == null) {
                return remainingProducers == 0 ? InputStatus.END_OF_INPUT : InputStatus.NOTHING_AVAILABLE;
            }
        } finally {
            lock.unlock();
        }

        try {
            output.collect(record);
            return InputStatus.MORE_AVAILABLE;
        } catch (Exception | Error error) {
            abort(error);
            throw error;
        } finally {
            lock.lock();
            try {
                processingRecords--;
                if (queuedRecords == 0 && processingRecords == 0 && failure == null) {
                    drained.complete(null);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /** Available when records are buffered, all producers ended, or the gate failed. */
    public CompletableFuture<Void> getAvailableFuture() {
        lock.lock();
        try {
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            if (queuedRecords > 0 || remainingProducers == 0) {
                return CompletableFuture.completedFuture(null);
            }
            if (available.isDone()) {
                available = new CompletableFuture<>();
            }
            return available.copy();
        } finally {
            lock.unlock();
        }
    }

    /** Completion waits for queued records and records already inside downstream write callbacks. */
    public CompletableFuture<Void> drainedFuture() {
        lock.lock();
        try {
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            if (queuedRecords == 0 && processingRecords == 0) {
                return CompletableFuture.completedFuture(null);
            }
            return drained.copy();
        } finally {
            lock.unlock();
        }
    }

    /** Abort wakes waiting producers, readers and checkpoint-drain observers. */
    public void abort(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        lock.lock();
        try {
            if (failure == null) {
                failure = cause;
                available.completeExceptionally(cause);
                drained.completeExceptionally(cause);
                spaceAvailable.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public int queuedRecords() {
        lock.lock();
        try {
            return queuedRecords;
        } finally {
            lock.unlock();
        }
    }

    private int validateSubpartition(ResultSubpartition<T> subpartition) {
        Objects.requireNonNull(subpartition, "subpartition");
        int producer = subpartition.getProducerIndex();
        if (producer < 0 || producer >= subpartitions.size() || subpartitions.get(producer) != subpartition) {
            throw new IllegalArgumentException("ResultSubpartition does not belong to this InputGate");
        }
        return producer;
    }

    private void checkFailure() throws Exception {
        if (failure == null) {
            return;
        }
        if (failure instanceof Exception error) {
            throw error;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(failure);
    }
}
