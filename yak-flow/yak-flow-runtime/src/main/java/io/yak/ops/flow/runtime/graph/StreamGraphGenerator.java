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
 * Compiles sink-rooted logical transformations into a validated StreamGraph.
 *
 * <p>Each upstream transformation is visited before its consumer, shared upstream
 * definitions are materialized once, and dependencies become StreamEdges.
 * This stage validates configuration without creating SourceReaders, Writers or
 * task threads. Callers must not concurrently mutate the input transformations.
 *
 * @author weifuwan
 */
public final class StreamGraphGenerator {

    private final List<SinkTransformation<?>> sinks;
    private final Configuration configuration;

    /**
     * Creates a generator for a single logical Sink.
     *
     * @param sink the output transformation
     * @param configuration the graph-planning configuration
     */
    public StreamGraphGenerator(SinkTransformation<?> sink, Configuration configuration) {
        this(List.of(Objects.requireNonNull(sink, "sink 不能为空")), configuration);
    }

    /**
     * Creates a generator for one or more logical Sinks.
     *
     * <p>The effective configuration is copied so subsequent caller changes do not
     * affect this generator.
     *
     * @param sinks the output transformations
     * @param configuration the graph-planning configuration
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
     * Generates an independently validated StreamGraph without opening runtime resources.
     *
     * <p>Resolves default parallelism and checks stable UIDs, connected types,
     * fan-in/fan-out restrictions and execution mode compatibility.
     *
     * @return a new graph describing the validated pipeline
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
     * Resolves execution mode using Source boundedness and configured preferences.
     *
     * <p>AUTOMATIC selects BATCH only for entirely bounded graphs. Explicit BATCH
     * rejects unbounded sources, while STREAMING accepts either kind.
     *
     * @param graph the already generated topology
     * @param configuration the effective execution configuration
     * @return BATCH or STREAMING for the current graph
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

    /** Per-invocation traversal state; never reused across graph compilations. */
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
        /** Resolves routing from explicit edge settings or the two operators' parallelism. */
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
