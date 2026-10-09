package io.yak.ops.flow.runtime.graph;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.flow.runtime.operators.OneInputOperatorFactory;
import io.yak.ops.flow.runtime.transformations.OneInputTransformation;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolved node definition in a StreamGraph.
 *
 * <p>Copies logical transformation properties during compilation without creating
 * SourceReaders, Operators or Writers. Factories and connectors must remain reusable
 * definitions rather than capturing active subtask resources.
 *
 * @author weifuwan
 */
public final class StreamNode {

    private final int id;
    private final String name;
    private final String uid;
    private final int declaredParallelism;
    private final int parallelism;
    private final Class<?> inputType;
    private final Class<?> outputType;
    private final Source<?, ?, ?> source;
    private final OneInputOperatorFactory<?, ?> operatorFactory;
    private final Sink<?> sink;
    private final Boundedness boundedness;

    private StreamNode(Transformation<?> transformation, int resolvedParallelism) {
        Objects.requireNonNull(transformation, "transformation 不能为空");
        if (resolvedParallelism <= 0) {
            throw new IllegalArgumentException("已解析的并行度必须大于 0");
        }
        if (transformation.getParallelism() != Transformation.DEFAULT_PARALLELISM
                && transformation.getParallelism() != resolvedParallelism) {
            throw new IllegalArgumentException("已解析的并行度与 Transformation 显式并行度不一致");
        }

        this.id = transformation.getId();
        this.name = transformation.getName();
        this.uid = transformation.getUid();
        this.declaredParallelism = transformation.getParallelism();
        this.parallelism = resolvedParallelism;
        this.outputType = transformation.getOutputType();

        switch (transformation) {
            case SourceTransformation<?> sourceTransformation -> {
                this.inputType = null;
                this.source = sourceTransformation.getSource();
                this.boundedness = sourceTransformation.getBoundedness();
                this.operatorFactory = null;
                this.sink = null;
            }
            case OneInputTransformation<?, ?> operatorTransformation -> {
                this.inputType = operatorTransformation.getInputType();
                this.source = null;
                this.boundedness = null;
                this.operatorFactory = operatorTransformation.getOperatorFactory();
                this.sink = null;
            }
            case SinkTransformation<?> sinkTransformation -> {
                this.inputType = sinkTransformation.getInputType();
                this.source = null;
                this.boundedness = null;
                this.operatorFactory = null;
                this.sink = sinkTransformation.getSink();
            }
            default ->
                throw new IllegalArgumentException(
                        "暂不支持的 Transformation 类型：" + transformation.getClass().getName());
        }
    }

    /**
 * Creates a resolved graph node from a logical transformation.
 *
 * @param transformation the validated logical operator definition
 * @param resolvedParallelism the positive parallelism determined by graph planning
 * @return a graph node with frozen execution properties
 */
    public static StreamNode fromTransformation(Transformation<?> transformation, int resolvedParallelism) {
        return new StreamNode(transformation, resolvedParallelism);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Returns the stable operator UID, or null if none was assigned. */
    public String getUid() {
        return uid;
    }

    /** Returns the declared parallelism, or the default sentinel if inherited. */
    public int getDeclaredParallelism() {
        return declaredParallelism;
    }

    /** Returns whether the node inherits default parallelism from configuration. */
    public boolean usesDefaultParallelism() {
        return declaredParallelism == Transformation.DEFAULT_PARALLELISM;
    }

    /** Returns the resolved parallelism, which must not be overwritten during deployment. */
    public int getParallelism() {
        return parallelism;
    }

    /** Returns the expected input type; Source nodes have no input type. */
    public Optional<Class<?>> getInputType() {
        return Optional.ofNullable(inputType);
    }

    /** Returns the Java output type; Sink nodes have Void output. */
    public Class<?> getOutputType() {
        return outputType;
    }

    public boolean isSource() {
        return source != null;
    }

    public boolean isOperator() {
        return operatorFactory != null;
    }

    public boolean isSink() {
        return sink != null;
    }

    public Optional<Source<?, ?, ?>> getSource() {
        return Optional.ofNullable(source);
    }

    public Optional<OneInputOperatorFactory<?, ?>> getOperatorFactory() {
        return Optional.ofNullable(operatorFactory);
    }

    public Optional<Sink<?>> getSink() {
        return Optional.ofNullable(sink);
    }

    /** Returns Source boundedness, absent for non-Source nodes. */
    public Optional<Boundedness> getBoundedness() {
        return Optional.ofNullable(boundedness);
    }

    /** Omits connector instances and their captured configuration from diagnostics. */
    @Override
    public String toString() {
        return "StreamNode{id=" + id + ", name='" + name + "', parallelism=" + parallelism + "}";
    }
}
