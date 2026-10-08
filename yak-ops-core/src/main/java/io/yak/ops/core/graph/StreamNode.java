package io.yak.ops.core.graph;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.core.api.operators.OneInputOperatorFactory;
import io.yak.ops.core.transformations.OneInputTransformation;
import io.yak.ops.core.transformations.SinkTransformation;
import io.yak.ops.core.transformations.SourceTransformation;
import java.util.Objects;
import java.util.Optional;

/**
 * StreamGraph 中一个已解析执行属性的节点。
 *
 * <p>节点只保留算子定义与类型信息，不创建 Source Reader、Operator 或 SinkWriter。
 * 构造时复制 Transformation 的节点属性，后续修改 Transformation 不影响这些属性。
 *
 * <p>Source、OperatorFactory、Sink 必须作为可复用的组件定义使用，
 * 不得在其中保存某个运行子任务专属的活动连接或 Writer。
 *
 * @author weifuwan
 */
public final class StreamNode {

    private final int id;
    private final String name;
    private final String uid;
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
            default -> throw new IllegalArgumentException("暂不支持的 Transformation 类型："
                    + transformation.getClass().getName());
        }
    }

    /**
     * 将一个已支持的逻辑 Transformation 转换为执行节点快照。
     *
     * @param transformation 逻辑转换节点
     * @param resolvedParallelism 已结合执行配置解析的正整数并行度
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

    /** 稳定算子 UID；未设置时返回 null。 */
    public String getUid() {
        return uid;
    }

    public int getParallelism() {
        return parallelism;
    }

    /** Source 没有输入类型；其他节点返回其期望的输入类型。 */
    public Optional<Class<?>> getInputType() {
        return Optional.ofNullable(inputType);
    }

    /** Sink 的逻辑输出类型为 Void。 */
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

    /** 仅 Source 节点具有 Boundedness。 */
    public Optional<Boundedness> getBoundedness() {
        return Optional.ofNullable(boundedness);
    }

    /** 不输出组件定义，避免日志意外泄漏连接配置。 */
    @Override
    public String toString() {
        return "StreamNode{id=" + id + ", name='" + name + "', parallelism=" + parallelism + "}";
    }
}
