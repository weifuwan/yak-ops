package io.yak.ops.flow.runtime.transformations;

import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.core.api.operators.KeySelector;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import io.yak.ops.flow.runtime.operators.OneInputOperatorFactory;
import java.util.List;
import java.util.Objects;

/**
 * Logical definition of a single-input operator and its upstream transformation.
 *
 * <p>Retains the upstream node and reusable operator factory, without creating tasks,
 * opening connections or processing records. Inputs and factories are immutable after
 * construction; each deployed subtask creates a new operator instance.
 *
 * @param <IN> the upstream output and this operator's input type
 * @param <OUT> the record type produced by this operator
 * @author weifuwan
 */
public class OneInputTransformation<IN, OUT> extends Transformation<OUT> {

    /** The unique upstream logical transformation. */
    private final Transformation<IN> input;

    /** Factory used to create independent operators, not a shared running instance. */
    private final OneInputOperatorFactory<IN, OUT> operatorFactory;

    /** Explicit edge routing, or null to infer it from resolved parallelism. */
    private StreamPartitioning inputPartitioning;

    private KeySelector<IN> inputKeySelector;

    public OneInputTransformation(
            Transformation<IN> input,
            String name,
            OneInputOperatorFactory<IN, OUT> operatorFactory,
            Class<OUT> outputType) {
        this(input, name, operatorFactory, outputType, DEFAULT_PARALLELISM);
    }

    public OneInputTransformation(
            Transformation<IN> input,
            String name,
            OneInputOperatorFactory<IN, OUT> operatorFactory,
            Class<OUT> outputType,
            int parallelism) {
        super(name, outputType, parallelism);
        this.input = Objects.requireNonNull(input, "input 不能为空");
        this.operatorFactory = Objects.requireNonNull(operatorFactory, "operatorFactory 不能为空");
    }

    /** Returns the single upstream transformation. */
    public final Transformation<IN> getInput() {
        return input;
    }

    /** Returns the input type inherited from the upstream transformation. */
    public final Class<IN> getInputType() {
        return input.getOutputType();
    }

    /** Returns the reusable operator factory without constructing an instance. */
    public final OneInputOperatorFactory<IN, OUT> getOperatorFactory() {
        return operatorFactory;
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
    public final void keyBy(KeySelector<IN> keySelector) {
        this.inputKeySelector = Objects.requireNonNull(keySelector, "keySelector 不能为空");
        this.inputPartitioning = StreamPartitioning.KEYED;
    }

    public final StreamPartitioning getInputPartitioning() {
        return inputPartitioning;
    }

    public final KeySelector<IN> getInputKeySelector() {
        return inputKeySelector;
    }

    /** Returns an immutable list containing the single upstream transformation. */
    @Override
    public final List<Transformation<?>> getInputs() {
        return List.of(input);
    }
}
