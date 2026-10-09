package io.yak.ops.flow.runtime.transformations;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.core.api.operators.KeySelector;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import java.util.List;
import java.util.Objects;

/**
 * Logical terminal Sink node of a pipeline.
 *
 * <p>Retains one upstream transformation and a reusable Sink definition, not a live
 * Writer or connection. The logical output type is {@link Void}. A Sink must remain
 * terminal and cannot serve as input to another operator.
 *
 * @param <T> the input record type consumed by this Sink
 * @author weifuwan
 */
public final class SinkTransformation<T> extends Transformation<Void> {

    /** The unique upstream logical transformation. */
    private final Transformation<T> input;

    /** Reusable Sink definition, not a running Writer instance. */
    private final Sink<T> sink;

    /** Explicit routing policy, or null to infer routing from parallelism. */
    private StreamPartitioning inputPartitioning;

    private KeySelector<T> inputKeySelector;

    public SinkTransformation(Transformation<T> input, String name, Sink<T> sink) {
        this(input, name, sink, DEFAULT_PARALLELISM);
    }

    public SinkTransformation(Transformation<T> input, String name, Sink<T> sink, int parallelism) {
        super(name, Void.class, parallelism);
        this.input = Objects.requireNonNull(input, "input 不能为空");
        this.sink = Objects.requireNonNull(sink, "sink 不能为空");
    }

    /** Returns the single upstream transformation. */
    public Transformation<T> getInput() {
        return input;
    }

    /** Returns the input record type produced by the upstream transformation. */
    public Class<T> getInputType() {
        return input.getOutputType();
    }

    /** Returns the reusable Sink definition without creating a Writer. */
    public Sink<T> getSink() {
        return sink;
    }

    /** Selects FORWARD or REBALANCE routing; default routing depends on parallelism. */
    public final void setInputPartitioning(StreamPartitioning partitioning) {
        Objects.requireNonNull(partitioning, "partitioning 不能为空");
        if (partitioning == StreamPartitioning.KEYED) {
            throw new IllegalArgumentException("KEYED 分区必须通过 keyBy() 提供稳定主键");
        }
        this.inputPartitioning = partitioning;
        this.inputKeySelector = null;
    }

    /** Uses a stable business key, such as a CDC primary key, for subtask affinity. */
    public final void keyBy(KeySelector<T> keySelector) {
        this.inputKeySelector = Objects.requireNonNull(keySelector, "keySelector 不能为空");
        this.inputPartitioning = StreamPartitioning.KEYED;
    }

    public final StreamPartitioning getInputPartitioning() {
        return inputPartitioning;
    }

    public final KeySelector<T> getInputKeySelector() {
        return inputKeySelector;
    }

    /** Returns an immutable list containing the single upstream transformation. */
    @Override
    public List<Transformation<?>> getInputs() {
        return List.of(input);
    }
}
