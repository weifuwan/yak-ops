package io.yak.ops.flow.runtime.graph;

import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.core.api.operators.KeySelector;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.flow.runtime.transformations.OneInputTransformation;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 将以 Sink 为终点的 Transformation 逻辑图转换为 StreamGraph。
 *
 * <p>从各 Sink 向上遍历，先转换上游，再为当前 Transformation 创建 StreamNode，
 * 并根据 getInputs() 自动连接 StreamEdge。共享的上游对象只转换一次。
 *
 * <p>本类只负责构图和配置校验，不启动 Source、Operator 或 SinkWriter。
 * 构图期间不得并发修改 Transformation 的名称、UID、并行度或输入关系。
 *
 * @author weifuwan
 */
public final class StreamGraphGenerator {

    private final List<SinkTransformation<?>> sinks;
    private final Configuration configuration;

    /**
     * 使用一个 Sink 构造执行图生成器。
     *
     * @param sink 数据流的终点
     * @param configuration 用于生成执行图的配置
     */
    public StreamGraphGenerator(SinkTransformation<?> sink, Configuration configuration) {
        this(List.of(Objects.requireNonNull(sink, "sink 不能为空")), configuration);
    }

    /**
     * 使用一个或多个 Sink 构造执行图生成器。
     *
     * <p>配置在构造时复制，之后调用方修改原始 Configuration 不会影响本生成器。
     *
     * @param sinks 一个或多个 Sink 逻辑节点
     * @param configuration 用于生成执行图的配置
     */
    public StreamGraphGenerator(Collection<? extends SinkTransformation<?>> sinks, Configuration configuration) {
        Objects.requireNonNull(sinks, "sinks 不能为空");
        if (sinks.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个 SinkTransformation");
        }
        List<SinkTransformation<?>> checkedSinks = new ArrayList<>();
        for (SinkTransformation<?> sink : sinks) {
            if (sink == null) {
                throw new IllegalArgumentException("SinkTransformation 不能为空");
            }
            checkedSinks.add(sink);
        }
        this.sinks = List.copyOf(checkedSinks);
        this.configuration = new Configuration(Objects.requireNonNull(configuration, "configuration 不能为空"));
    }

    /**
     * 生成拓扑完整、节点参数已解析的 StreamGraph。
     *
     * <p>按照配置解析默认并行度并校验 UID；图内部负责校验数据类型、连接数量、
     * 重复节点和无环关系。显式使用 BATCH 时不允许存在无界 Source。
     *
     * <p>调用该方法不会创建任何运行资源，可重复调用并生成独立的图对象。
     *
     * @return 已生成的 StreamGraph
     */
    public StreamGraph generate() {
        Integer defaultParallelism = configuration.get(CoreOptions.DEFAULT_PARALLELISM);
        if (defaultParallelism == null || defaultParallelism <= 0) {
            throw new IllegalArgumentException("parallelism.default 必须为正整数");
        }
        Boolean generateUids = configuration.get(PipelineOptions.AUTO_GENERATE_UIDS);
        if (generateUids == null) {
            throw new IllegalArgumentException("pipeline.auto-generate-uids 不能为空");
        }

        GraphVisitor visitor = new GraphVisitor(defaultParallelism, generateUids);
        for (SinkTransformation<?> sink : sinks) {
            visitor.visit(sink);
        }

        StreamGraph graph = new StreamGraph(visitor.nodes, visitor.edges);
        resolveRuntimeMode(graph, configuration);
        return graph;
    }

    /**
     * 结合 Source 有界性与配置解析实际运行模式。
     *
     * <p>AUTOMATIC 在所有 Source 均有界时解析为 BATCH，否则为 STREAMING；
     * 显式 BATCH 与无界 Source 不兼容；STREAMING 同时支持有界和无界 Source。
     *
     * <p>现阶段 StreamGraph 尚不存储运行模式，因此本方法可供后续执行准备阶段复用。
     *
     * @param graph 已生成的 StreamGraph
     * @param configuration 执行配置
     * @return 具体运行模式（BATCH 或 STREAMING）
     */
    public static RuntimeExecutionMode resolveRuntimeMode(StreamGraph graph, Configuration configuration) {
        Objects.requireNonNull(graph, "graph 不能为空");
        Objects.requireNonNull(configuration, "configuration 不能为空");

        RuntimeExecutionMode configured = configuration.get(ExecutionOptions.RUNTIME_MODE);
        if (configured == null) {
            throw new IllegalArgumentException("execution.runtime-mode 不能为空");
        }
        return switch (configured) {
            case AUTOMATIC -> graph.isBounded() ? RuntimeExecutionMode.BATCH : RuntimeExecutionMode.STREAMING;
            case BATCH -> {
                if (!graph.isBounded()) {
                    throw new IllegalArgumentException("BATCH 模式不支持无界 Source");
                }
                yield RuntimeExecutionMode.BATCH;
            }
            case STREAMING -> RuntimeExecutionMode.STREAMING;
        };
    }

    /** 单次 generate() 调用内部的遍历状态，不跨构图复用。 */
    private static final class GraphVisitor {

        private final int defaultParallelism;
        private final boolean autoGenerateUids;

        private final Set<Transformation<?>> visiting = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Transformation<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        private final List<StreamNode> nodes = new ArrayList<>();
        private final List<StreamEdge> edges = new ArrayList<>();

        private GraphVisitor(int defaultParallelism, boolean autoGenerateUids) {
            this.defaultParallelism = defaultParallelism;
            this.autoGenerateUids = autoGenerateUids;
        }

        private void visit(Transformation<?> transformation) {
            Objects.requireNonNull(transformation, "Transformation 不能为空");
            if (visiting.contains(transformation)) {
                throw new IllegalArgumentException("Transformation 存在循环依赖，节点 ID=" + transformation.getId());
            }
            if (visited.contains(transformation)) {
                return;
            }

            visiting.add(transformation);
            try {
                List<Transformation<?>> inputs =
                        Objects.requireNonNull(transformation.getInputs(), "getInputs() 不能返回 null");
                for (Transformation<?> input : inputs) {
                    visit(Objects.requireNonNull(input, "上游 Transformation 不能为空"));
                }

                if (!autoGenerateUids && transformation.getUid() == null) {
                    throw new IllegalArgumentException("关闭自动 UID 生成后，所有节点必须指定稳定 UID，节点 ID=" + transformation.getId());
                }

                int parallelism = transformation.getParallelism() == Transformation.DEFAULT_PARALLELISM
                        ? defaultParallelism
                        : transformation.getParallelism();
                nodes.add(StreamNode.fromTransformation(transformation, parallelism));
                for (Transformation<?> input : inputs) {
                    edges.add(createEdge(input, transformation));
                }
                visited.add(transformation);
            } finally {
                visiting.remove(transformation);
            }
        }
        /** 将下游声明与两端并行度解析为明确的边策略；只有用户声明 KEYED 才使用键哈希。 */
        private StreamEdge createEdge(Transformation<?> input, Transformation<?> target) {
            StreamPartitioning requested = null;
            KeySelector<?> selector = null;
            if (target instanceof OneInputTransformation<?, ?> operator) {
                requested = operator.getInputPartitioning();
                selector = operator.getInputKeySelector();
            } else if (target instanceof SinkTransformation<?> sink) {
                requested = sink.getInputPartitioning();
                selector = sink.getInputKeySelector();
            }
            int upstream = input.getParallelism() == Transformation.DEFAULT_PARALLELISM
                    ? defaultParallelism
                    : input.getParallelism();
            int downstream = target.getParallelism() == Transformation.DEFAULT_PARALLELISM
                    ? defaultParallelism
                    : target.getParallelism();
            StreamPartitioning partitioning = requested == null
                    ? (upstream == downstream ? StreamPartitioning.FORWARD : StreamPartitioning.REBALANCE)
                    : requested;
            return new StreamEdge(input.getId(), target.getId(), partitioning, selector);
        }
    }
}
