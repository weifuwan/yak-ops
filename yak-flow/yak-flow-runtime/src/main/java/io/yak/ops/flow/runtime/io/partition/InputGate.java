package io.yak.ops.flow.runtime.io.partition;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.flow.runtime.checkpoint.CheckpointBarrier;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Local consumer-side input gate for one downstream subtask.
 *
 * <p>Each producer owns a distinct ResultSubpartition registered with this gate. The gate arbitrates a single bounded buffer
 * budget shared by all producers, and delivers available records round-robin on the consumer Task
 * thread. Producers block on a condition, not a timed polling loop.
 */
public final class InputGate<T> {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition spaceAvailable = lock.newCondition();
    private final List<ResultSubpartition<T>> subpartitions;
    private final boolean[] producerFinished;
    private final boolean[] barrierBlocked;
    // Producer-side ordering fence: after a barrier is enqueued, post-barrier records must
    // not fill the *shared* capacity needed by other producers' pre-barrier records.
    private final long[] producerBarriers;
    private long enqueuedCheckpointId = -1;
    private long declinedCheckpointId;
    private long pendingDeclineNotification;
    private long aligningCheckpointId = -1;
    private long lastAlignedCheckpointId;
    private int barrierCount;
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
        this.barrierBlocked = new boolean[producerCount];
        this.producerBarriers = new long[producerCount];
        this.remainingProducers = producerCount;
        // The producer ResultPartition owns each subpartition and registers it before Task start.
        List<ResultSubpartition<T>> channels = new ArrayList<>(producerCount);
        for (int i = 0; i < producerCount; i++) {
            channels.add(null);
        }
        this.subpartitions = channels;
    }

    /** Attach one producer-owned ResultSubpartition to the matching input channel. */
    void registerSubpartition(ResultSubpartition<T> subpartition) {
        Objects.requireNonNull(subpartition, "subpartition");
        int producer = subpartition.getProducerIndex();
        lock.lock();
        try {
            if (producer < 0 || producer >= subpartitions.size() || subpartitions.get(producer) != null) {
                throw new IllegalArgumentException("Duplicate or invalid producer input channel: " + producer);
            }
            subpartitions.set(producer, subpartition);
        } finally {
            lock.unlock();
        }
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
            while (queuedRecords == capacity
                    || producerBarriers[producer] > lastAlignedCheckpointId) {
                spaceAvailable.await();
                checkFailure();
                if (producerFinished[producer]) {
                    throw new IllegalStateException("Producer has already finished: " + producer);
                }
            }
            subpartition.elements.addLast(new RecordElement<>(record));
            queuedRecords++;
            if (drained.isDone()) {
                drained = new CompletableFuture<>();
            }
            available.complete(null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Barriers are ordered behind preceding records but do not consume the data-buffer budget.
     * This avoids a blocked producer preventing another producer's alignment barrier.
     */
    void enqueueBarrier(ResultSubpartition<T> subpartition, CheckpointBarrier barrier) {
        Objects.requireNonNull(barrier, "barrier");
        int producer = validateSubpartition(subpartition);
        lock.lock();
        try {
            if (failure != null) {
                throw new IllegalStateException("InputGate already failed", failure);
            }
            if (producerFinished[producer]) {
                throw new IllegalStateException("Producer has already finished: " + producer);
            }
            long id = barrier.checkpointId();
            if (id <= lastAlignedCheckpointId) {
                throw new IllegalStateException("Stale checkpoint barrier: " + id);
            }
            if (id <= declinedCheckpointId) {
                return; // The coordinator already declined this in-flight checkpoint.
            }
            if (enqueuedCheckpointId != -1 && enqueuedCheckpointId != id) {
                throw new IllegalStateException("Overlapping checkpoint barriers");
            }
            if (producerBarriers[producer] == id) {
                throw new IllegalStateException("Duplicate producer checkpoint barrier: " + id);
            }
            enqueuedCheckpointId = id;
            producerBarriers[producer] = id;
            // EndOfPartition on another channel without this barrier declines the checkpoint,
            // not the data pipeline.
            for (int i = 0; i < producerFinished.length; i++) {
                if (producerFinished[i] && producerBarriers[i] != id) {
                    declineCheckpointLocked(id);
                    return;
                }
            }
            subpartition.elements.addLast(barrier);
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
            --remainingProducers;
            // A finished producer cannot supply the missing alignment barrier.
            if (enqueuedCheckpointId > 0 && producerBarriers[producer] != enqueuedCheckpointId) {
                declineCheckpointLocked(enqueuedCheckpointId);
            }
            spaceAvailable.signalAll();
            if (remainingProducers == 0) {
                available.complete(null);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Handle a fully aligned barrier only after all preceding records have been delivered. */
    @FunctionalInterface
    public interface BarrierHandler {
        void onBarrier(long checkpointId) throws Exception;

        /** Checkpoint-only failure: the input task must keep processing regular data. */
        default void onCheckpointDeclined(long checkpointId) throws Exception {
            throw new UnsupportedOperationException("Checkpoint decline handler is missing");
        }
    }

    /** Compatibility overload for pure data inputs that never receive a checkpoint barrier. */
    public InputStatus emitNext(ReaderOutput<T> output) throws Exception {
        return emitNext(output, id -> {
            throw new UnsupportedOperationException("Checkpoint barrier handler is missing");
        });
    }

    /** One mailbox step: data record, partial alignment event, or fully aligned barrier. */
    @SuppressWarnings("unchecked")
    public InputStatus emitNext(ReaderOutput<T> output, BarrierHandler barrierHandler) throws Exception {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(barrierHandler, "barrierHandler");
        T record = null;
        long completedBarrier = -1;
        long declinedBarrier = -1;
        boolean processing = false;
        boolean partialBarrier = false;
        lock.lock();
        try {
            checkFailure();
            if (pendingDeclineNotification > 0) {
                declinedBarrier = pendingDeclineNotification;
                pendingDeclineNotification = 0;
            }
            for (int i = 0; declinedBarrier < 0 && i < subpartitions.size(); i++) {
                int channel = (nextInput + i) % subpartitions.size();
                if (barrierBlocked[channel]) {
                    continue;
                }
                ResultSubpartition<T> partition = subpartitions.get(channel);
                Object item = partition == null ? null : partition.elements.pollFirst();
                if (item == null) {
                    continue;
                }
                nextInput = (channel + 1) % subpartitions.size();
                if (item instanceof CheckpointBarrier barrier) {
                    long id = barrier.checkpointId();
                    if (id <= lastAlignedCheckpointId
                            || (aligningCheckpointId != -1 && aligningCheckpointId != id)) {
                        throw new IllegalStateException("Overlapping or stale checkpoint barrier: " + id);
                    }
                    if (aligningCheckpointId == -1) {
                        aligningCheckpointId = id;
                    }
                    barrierBlocked[channel] = true;
                    if (++barrierCount == subpartitions.size()) {
                        Arrays.fill(barrierBlocked, false);
                        aligningCheckpointId = -1;
                        lastAlignedCheckpointId = id;
                        enqueuedCheckpointId = -1;
                        barrierCount = 0;
                        spaceAvailable.signalAll();
                        completedBarrier = id;
                        processingRecords++;
                        processing = true;
                        available.complete(null);
                    } else {
                        partialBarrier = true;
                    }
                } else {
                    record = ((RecordElement<T>) item).value();
                    queuedRecords--;
                    processingRecords++;
                    processing = true;
                    spaceAvailable.signal();
                }
                break;
            }
            if (!processing && !partialBarrier && completedBarrier < 0
                    && declinedBarrier < 0 && record == null) {
                if (remainingProducers == 0 && !anyReadable() && aligningCheckpointId > 0) {
                    // Defensive fallback for an incomplete alignment whose producers all ended.
                    long id = aligningCheckpointId;
                    declineCheckpointLocked(id);
                    declinedBarrier = pendingDeclineNotification;
                    pendingDeclineNotification = 0;
                } else if (remainingProducers == 0 && !anyBuffered() && aligningCheckpointId == -1) {
                    return InputStatus.END_OF_INPUT;
                } else {
                    return InputStatus.NOTHING_AVAILABLE;
                }
            }
        } catch (Exception | Error error) {
            abort(error);
            throw error;
        } finally {
            lock.unlock();
        }

        try {
            if (declinedBarrier > 0) {
                barrierHandler.onCheckpointDeclined(declinedBarrier);
            } else if (completedBarrier > 0) {
                barrierHandler.onBarrier(completedBarrier);
            } else if (record != null) {
                output.collect(record);
            }
            return InputStatus.MORE_AVAILABLE;
        } catch (Exception | Error error) {
            abort(error);
            throw error;
        } finally {
            if (processing) {
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
    }

    /**
     * Drop an aborted checkpoint's control events, without dropping a single data record.
     * Called by the coordinator on failure and by the gate on an input EndOfPartition race.
     */
    public void declineCheckpoint(long checkpointId) {
        if (checkpointId <= 0) {
            throw new IllegalArgumentException("Checkpoint ID must be positive");
        }
        lock.lock();
        try {
            declineCheckpointLocked(checkpointId);
        } finally {
            lock.unlock();
        }
    }

    private void declineCheckpointLocked(long checkpointId) {
        if (checkpointId <= declinedCheckpointId || checkpointId <= lastAlignedCheckpointId) {
            return;
        }
        declinedCheckpointId = checkpointId;
        pendingDeclineNotification = checkpointId;
        enqueuedCheckpointId = -1;
        aligningCheckpointId = -1;
        barrierCount = 0;
        Arrays.fill(barrierBlocked, false);
        Arrays.fill(producerBarriers, 0);
        // Keep all records, including those that were after a now-declined barrier.
        for (ResultSubpartition<T> partition : subpartitions) {
            if (partition != null) {
                partition.elements.removeIf(item -> item instanceof CheckpointBarrier barrier
                        && barrier.checkpointId() <= checkpointId);
            }
        }
        spaceAvailable.signalAll();
        available.complete(null);
    }

    private boolean anyBuffered() {
        return subpartitions.stream().anyMatch(partition -> partition != null && !partition.elements.isEmpty());
    }

    private boolean anyReadable() {
        for (int i = 0; i < subpartitions.size(); i++) {
            if (!barrierBlocked[i] && subpartitions.get(i) != null
                    && !subpartitions.get(i).elements.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private record RecordElement<T>(T value) {}

    /** Available when records are buffered, all producers ended, or the gate failed. */
    public CompletableFuture<Void> getAvailableFuture() {
        lock.lock();
        try {
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            if (pendingDeclineNotification > 0 || anyReadable() || remainingProducers == 0) {
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
