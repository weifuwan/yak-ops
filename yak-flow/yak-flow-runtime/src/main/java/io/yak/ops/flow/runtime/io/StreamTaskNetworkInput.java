package io.yak.ops.flow.runtime.io;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.flow.runtime.io.partition.InputGate;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** StreamTaskInput adapter for the embedded consumer-side InputGate. */
public final class StreamTaskNetworkInput<T> implements StreamTaskInput<T> {

    private final InputGate<T> inputGate;

    public StreamTaskNetworkInput(InputGate<T> inputGate) {
        this.inputGate = Objects.requireNonNull(inputGate, "inputGate");
    }

    @Override
    public int getInputIndex() {
        return 0;
    }

    @Override
    public InputStatus emitNext(ReaderOutput<T> output) throws Exception {
        return inputGate.emitNext(output);
    }

    /** Forward both data and aligned control events on the consumer's mailbox thread. */
    public InputStatus emitNext(ReaderOutput<T> output, InputGate.BarrierHandler barrierHandler)
            throws Exception {
        return inputGate.emitNext(output, barrierHandler);
    }

    @Override
    public CompletableFuture<Void> getAvailableFuture() {
        return inputGate.getAvailableFuture();
    }
}
