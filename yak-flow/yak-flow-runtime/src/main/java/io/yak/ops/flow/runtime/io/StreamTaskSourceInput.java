package io.yak.ops.flow.runtime.io;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.flow.runtime.operators.SourceOperator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Adapt a SourceOperator to StreamTaskInput without managing its Reader or SourceCoordinator.
 * Unlike network input, the records come directly from SourceReader.pollNext().
 */
public final class StreamTaskSourceInput<T> implements StreamTaskInput<T> {

    private final SourceOperator<T, ? extends SourceSplit> operator;

    public StreamTaskSourceInput(SourceOperator<T, ? extends SourceSplit> operator) {
        this.operator = Objects.requireNonNull(operator, "operator");
    }

    @Override
    public int getInputIndex() {
        return 0;
    }

    @Override
    public InputStatus emitNext(ReaderOutput<T> output) throws Exception {
        return operator.emitNext(Objects.requireNonNull(output, "output"));
    }

    @Override
    public CompletableFuture<Void> getAvailableFuture() {
        return operator.isAvailable();
    }
}
