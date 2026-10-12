package io.yak.ops.flow.runtime.io.partition;

import io.yak.ops.flow.runtime.checkpoint.CheckpointBarrier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Producer-side result partition owned by one upstream StreamTask.
 *
 * <p>Each producer allocates one ResultSubpartition for every target and registers it with the
 * target InputGate. The consumer InputGate controls
 * their shared bounded buffer budget; the producer never owns or polls an input queue.
 */
public final class ResultPartition<T> {

    private final int producerIndex;
    private final List<ResultSubpartition<T>> subpartitions;
    private boolean dataFinished;
    private boolean finished;
    private CompletableFuture<Void> endOfDataAcknowledged;

    public ResultPartition(int producerIndex, List<InputGate<T>> downstreamGates) {
        Objects.requireNonNull(downstreamGates, "downstreamGates");
        if (downstreamGates.isEmpty()) {
            throw new IllegalArgumentException("ResultPartition requires at least one downstream gate");
        }
        if (producerIndex < 0) {
            throw new IllegalArgumentException("Invalid producer subtask index");
        }
        for (InputGate<T> gate : downstreamGates) {
            if (gate == null || producerIndex >= gate.getNumberOfInputChannels()) {
                throw new IllegalArgumentException("The producer index must exist in every downstream gate");
            }
        }
        this.producerIndex = producerIndex;
        List<ResultSubpartition<T>> partitions = new ArrayList<>(downstreamGates.size());
        for (InputGate<T> gate : downstreamGates) {
            ResultSubpartition<T> subpartition = new ResultSubpartition<>(gate, producerIndex);
            gate.registerSubpartition(subpartition);
            partitions.add(subpartition);
        }
        this.subpartitions = List.copyOf(partitions);
    }

    public int getProducerIndex() {
        return producerIndex;
    }

    public int getNumberOfSubpartitions() {
        return subpartitions.size();
    }

    /** Write into exactly one target channel chosen by RecordWriterOutput's partitioner. */
    public void emitRecord(int channel, T record) throws Exception {
        if (dataFinished || finished) {
            throw new IllegalStateException("Cannot emit data after EndOfData or physical finish");
        }
        subpartitions.get(channel).emitRecord(record);
    }

    /** Broadcast once to every physical channel, after all earlier records from this producer. */
    public void broadcastBarrier(long checkpointId) {
        if (finished) {
            throw new IllegalStateException("Cannot emit barrier after result partition finished");
        }
        CheckpointBarrier barrier = new CheckpointBarrier(checkpointId);
        for (ResultSubpartition<T> subpartition : subpartitions) {
            subpartition.emitBarrier(barrier);
        }
    }

    /** Send logical end-of-data while keeping checkpoint-control channels open. */
    public CompletableFuture<Void> endData() {
        if (dataFinished || finished) {
            throw new IllegalStateException("ResultPartition has already ended data or finished");
        }
        dataFinished = true;
        List<CompletableFuture<Void>> acknowledgments = new ArrayList<>(subpartitions.size());
        for (ResultSubpartition<T> subpartition : subpartitions) {
            acknowledgments.add(subpartition.endData());
        }
        endOfDataAcknowledged = CompletableFuture.allOf(acknowledgments.toArray(CompletableFuture[]::new));
        return endOfDataAcknowledged.copy();
    }

    /** Physically close all channels after the required EndOfData acknowledgments. */
    public void finish() {
        if (finished) {
            throw new IllegalStateException("ResultPartition finished twice");
        }
        if (dataFinished
                && (endOfDataAcknowledged == null
                        || !endOfDataAcknowledged.isDone()
                        || endOfDataAcknowledged.isCompletedExceptionally())) {
            throw new IllegalStateException("ResultPartition cannot finish before successful EndOfData ACK");
        }
        finished = true;
        for (ResultSubpartition<T> subpartition : subpartitions) {
            subpartition.finish();
        }
    }
}
