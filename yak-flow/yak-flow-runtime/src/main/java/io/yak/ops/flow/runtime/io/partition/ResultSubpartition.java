package io.yak.ops.flow.runtime.io.partition;

import io.yak.ops.flow.runtime.checkpoint.CheckpointBarrier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** One producer-to-consumer physical channel. All queue access is serialized by its InputGate. */
public final class ResultSubpartition<T> {

    // Ordered data and control events share the same producer-to-consumer FIFO.
    final Deque<Object> elements = new ArrayDeque<>();
    private final InputGate<T> gate;
    private final int producerIndex;

    ResultSubpartition(InputGate<T> gate, int producerIndex) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.producerIndex = producerIndex;
    }

    public int getProducerIndex() {
        return producerIndex;
    }

    public void emitRecord(T record) throws Exception {
        gate.enqueue(this, record);
    }

    public void emitBarrier(CheckpointBarrier barrier) {
        gate.enqueueBarrier(this, barrier);
    }

    public void finish() {
        gate.finishProducer(this);
    }
}
