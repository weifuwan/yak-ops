package io.yak.ops.flow.runtime.io.partition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** One producer-to-consumer physical channel. All queue access is serialized by its InputGate. */
public final class ResultSubpartition<T> {

    final Deque<T> records = new ArrayDeque<>();
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

    public void finish() {
        gate.finishProducer(this);
    }
}
