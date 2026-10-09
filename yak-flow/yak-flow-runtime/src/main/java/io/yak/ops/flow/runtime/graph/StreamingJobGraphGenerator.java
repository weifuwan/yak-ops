package io.yak.ops.flow.runtime.graph;

import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.sink.SupportsWriterState;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.flow.runtime.checkpoint.FileCheckpointStore;
import io.yak.ops.flow.runtime.configuration.RuntimeOptions;
import io.yak.ops.flow.runtime.jobgraph.JobEdge;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import io.yak.ops.flow.runtime.jobgraph.JobVertex;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compiles a StreamGraph into deployable JobVertices and cross-task edges.
 *
 * <p>All validation is performed before any SourceReader, SinkWriter or task thread is created.
 * The current embedded runtime supports a single linear chain; checkpointed graphs are never chained.
 */
public final class StreamingJobGraphGenerator {

    private static final int MAX_PARALLELISM = 16;
    private static final int MAX_JOB_SUBTASKS = 64;
    private static final int MAX_CHANNEL_CAPACITY = 4096;

    private final StreamGraph streamGraph;
    private final Configuration configuration;

    public StreamingJobGraphGenerator(StreamGraph streamGraph, Configuration configuration) {
        this.streamGraph = Objects.requireNonNull(streamGraph, "streamGraph");
        this.configuration = new Configuration(Objects.requireNonNull(configuration, "configuration"));
    }

    /**
     * Compiles deployable vertices and edges after validating the graph and runtime policies.
     *
     * <p>Planning must finish before any Reader, Writer or Task thread is created.
     *
     * @return the physical execution graph definition
     */
    public JobGraph generate() {
        validateConfiguration();
        validateTopology();
        validateCheckpoint();

        List<StreamNode> nodes = streamGraph.getTopologicalNodes();
        int maxParallelism = configuration.get(PipelineOptions.MAX_PARALLELISM);
        List<JobVertex> vertices = new ArrayList<>();
        List<JobEdge> edges = new ArrayList<>();
        boolean chainingEnabled = !checkpointEnabled()
                && nodes.stream().allMatch(node -> node.getParallelism() == 1)
                && streamGraph.getStreamEdges().stream()
                        .allMatch(edge -> edge.partitioning() == StreamPartitioning.FORWARD);

        if (chainingEnabled) {
            vertices.add(new JobVertex(nodes, maxParallelism));
        } else {
            for (StreamNode node : nodes) {
                vertices.add(new JobVertex(List.of(node), maxParallelism));
            }
            for (int i = 1; i < vertices.size(); i++) {
                JobVertex upstream = vertices.get(i - 1);
                JobVertex downstream = vertices.get(i);
                StreamEdge edge = streamGraph
                        .getOutEdges(upstream.getTailOperator().getId())
                        .getFirst();
                edges.add(new JobEdge(upstream.getId(), downstream.getId(), edge));
            }
        }

        RuntimeExecutionMode mode = StreamGraphGenerator.resolveRuntimeMode(streamGraph, configuration);
        return new JobGraph(JobID.generate(), streamGraph, mode, configuration, vertices, edges);
    }

    private void validateConfiguration() {
        int maxRestarts = configuration.get(ExecutionOptions.MAX_RESTART_ATTEMPTS);
        if (maxRestarts < 0) {
            throw new IllegalArgumentException("execution.restart.max-attempts 不能为负数");
        }
        if (maxRestarts > 0 && !checkpointEnabled()) {
            throw new UnsupportedOperationException("自动恢复必须启用持久化 Source → Sink Checkpoint（不能从头重复启动）");
        }
        int maxParallelism = configuration.get(PipelineOptions.MAX_PARALLELISM);
        if (maxParallelism <= 0 || maxParallelism > 32768) {
            throw new IllegalArgumentException("pipeline.max-parallelism 必须在 1 至 32768 之间");
        }
        int defaultParallelism = configuration.get(CoreOptions.DEFAULT_PARALLELISM);
        if (defaultParallelism <= 0) {
            throw new IllegalArgumentException("parallelism.default 必须为正整数");
        }
        boolean autoGenerateUids = configuration.get(PipelineOptions.AUTO_GENERATE_UIDS);
        for (StreamNode node : streamGraph.getStreamNodes()) {
            if (node.getParallelism() > maxParallelism) {
                throw new IllegalArgumentException("maxParallelism 不能小于算子并行度：" + node.getId());
            }
            if (node.usesDefaultParallelism() && node.getParallelism() != defaultParallelism) {
                throw new IllegalArgumentException("StreamNode " + node.getId() + " 的默认并行度与提交配置不一致："
                        + node.getParallelism() + " != " + defaultParallelism);
            }
            if (!autoGenerateUids && node.getUid() == null) {
                throw new IllegalArgumentException("关闭自动 UID 生成后，所有节点必须指定稳定 UID：" + node.getId());
            }
        }

        Duration interval = configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL);
        Duration timeout = configuration.get(CheckpointingOptions.CHECKPOINTING_TIMEOUT);
        Duration minPause = configuration.get(CheckpointingOptions.MIN_PAUSE_BETWEEN_CHECKPOINTS);
        if (interval.isNegative()
                || timeout.isNegative()
                || timeout.isZero()
                || minPause.isNegative()
                || configuration.get(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS) <= 0) {
            throw new IllegalArgumentException("Checkpoint 配置不合法");
        }
    }

    private void validateTopology() {
        List<StreamNode> nodes = streamGraph.getTopologicalNodes();
        if (streamGraph.getSourceNodes().size() != 1
                || streamGraph.getSinkNodes().size() != 1
                || nodes.size() < 2) {
            throw new UnsupportedOperationException("当前嵌入式 Runtime 仅支持一个 Source 和一个 Sink");
        }
        int subtasks = 0;
        for (int i = 0; i < nodes.size(); i++) {
            StreamNode node = nodes.get(i);
            if (node.getParallelism() <= 0 || node.getParallelism() > MAX_PARALLELISM) {
                throw new UnsupportedOperationException("单个算子的并行度必须在 1 到 " + MAX_PARALLELISM + " 之间：" + node.getId());
            }
            subtasks += node.getParallelism();
            if (i == 0) {
                if (!node.isSource() || !streamGraph.getInEdges(node.getId()).isEmpty()) {
                    throw new UnsupportedOperationException("线性图的首节点必须是 Source");
                }
            } else {
                List<StreamEdge> incoming = streamGraph.getInEdges(node.getId());
                if (incoming.size() != 1
                        || incoming.getFirst().sourceId() != nodes.get(i - 1).getId()) {
                    throw new UnsupportedOperationException("目前不支持多输入或分叉图");
                }
            }
            if (i == nodes.size() - 1) {
                if (!node.isSink() || !streamGraph.getOutEdges(node.getId()).isEmpty()) {
                    throw new UnsupportedOperationException("线性图的末节点必须是 Sink");
                }
            } else {
                List<StreamEdge> outgoing = streamGraph.getOutEdges(node.getId());
                if (outgoing.size() != 1
                        || outgoing.getFirst().targetId() != nodes.get(i + 1).getId()) {
                    throw new UnsupportedOperationException("目前不支持多 Sink 或分叉数据路由");
                }
                if (i > 0 && !node.isOperator()) {
                    throw new UnsupportedOperationException("线性图中间只允许单输入 Operator");
                }
            }
        }
        if (subtasks > MAX_JOB_SUBTASKS) {
            throw new UnsupportedOperationException("单个 Job 子任务数量上限为 " + MAX_JOB_SUBTASKS);
        }
        int capacity = configuration.get(RuntimeOptions.CHANNEL_CAPACITY);
        if (capacity <= 0 || capacity > MAX_CHANNEL_CAPACITY) {
            throw new IllegalArgumentException(
                    "execution.local-channel.capacity 必须在 1 到 " + MAX_CHANNEL_CAPACITY + " 之间");
        }
    }

    private void validateCheckpoint() {
        if (!checkpointEnabled()) {
            return;
        }
        String directory = configuration.get(CheckpointingOptions.STATE_DIRECTORY);
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("启用 Checkpoint 或恢复时必须指定状态目录");
        }
        if (configuration.get(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS) != 1) {
            throw new UnsupportedOperationException("当前 Checkpoint 只允许一次在途快照");
        }
        for (StreamNode sink : streamGraph.getSinkNodes()) {
            if (sink.getSink().orElseThrow() instanceof SupportsWriterState<?, ?> stateful
                    && stateful.getWriterStateSerializer() == null) {
                throw new IllegalArgumentException("Stateful Sink requires a non-null writer state serializer");
            }
        }
        FileCheckpointStore.graphSignature(streamGraph, configuration.get(PipelineOptions.MAX_PARALLELISM));
    }

    private boolean checkpointEnabled() {
        return !configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL).isZero()
                || configuration.get(CheckpointingOptions.RESTORE_LATEST);
    }
}
