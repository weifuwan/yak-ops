package io.yak.ops.flow.runtime.graph;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.sink.SupportsWriterState;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.flow.runtime.checkpoint.LocalCompletedCheckpointStore;
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
        if (configuration.get(RuntimeOptions.SCHEMA_EVOLUTION_ENABLED)) {
            Duration checkpointInterval = configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL);
            if (checkpointInterval.isNegative() || checkpointInterval.isZero()) {
                throw new UnsupportedOperationException(
                        "Runtime schema evolution requires periodic checkpoints for schema/binlog recovery consistency");
            }
        }
        int maxRestarts = configuration.get(ExecutionOptions.MAX_RESTART_ATTEMPTS);
        if (maxRestarts < 0) {
            throw new IllegalArgumentException("execution.restart.max-attempts must not be negative");
        }
        if (maxRestarts > 0 && !checkpointEnabled()) {
            throw new UnsupportedOperationException(
                    "Automatic recovery requires persistent Source-to-Sink checkpoints to avoid replay from the beginning");
        }
        int maxParallelism = configuration.get(PipelineOptions.MAX_PARALLELISM);
        if (maxParallelism <= 0 || maxParallelism > 32768) {
            throw new IllegalArgumentException("pipeline.max-parallelism must be between 1 and 32768");
        }
        int defaultParallelism = configuration.get(CoreOptions.DEFAULT_PARALLELISM);
        if (defaultParallelism <= 0) {
            throw new IllegalArgumentException("parallelism.default must be positive");
        }
        boolean autoGenerateUids = configuration.get(PipelineOptions.AUTO_GENERATE_UIDS);
        for (StreamNode node : streamGraph.getStreamNodes()) {
            if (node.getParallelism() > maxParallelism) {
                throw new IllegalArgumentException(
                        "maxParallelism must be at least the operator parallelism: " + node.getId());
            }
            if (node.usesDefaultParallelism() && node.getParallelism() != defaultParallelism) {
                throw new IllegalArgumentException(
                        "StreamNode " + node.getId() + " default parallelism differs from the submitted configuration: "
                                + node.getParallelism() + " != " + defaultParallelism);
            }
            if (!autoGenerateUids && node.getUid() == null) {
                throw new IllegalArgumentException(
                        "Stable operator UID is required when automatic UID generation is disabled: " + node.getId());
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
            throw new IllegalArgumentException("Invalid checkpoint configuration");
        }
    }

    private void validateTopology() {
        List<StreamNode> nodes = streamGraph.getTopologicalNodes();
        if (streamGraph.getSourceNodes().size() != 1
                || streamGraph.getSinkNodes().size() != 1
                || nodes.size() < 2) {
            throw new UnsupportedOperationException("Embedded runtime supports exactly one Source and one Sink");
        }
        int subtasks = 0;
        for (int i = 0; i < nodes.size(); i++) {
            StreamNode node = nodes.get(i);
            if (node.getParallelism() <= 0 || node.getParallelism() > MAX_PARALLELISM) {
                throw new UnsupportedOperationException("Operator parallelism must be between 1 and " + MAX_PARALLELISM
                        + " for operator: " + node.getId());
            }
            subtasks += node.getParallelism();
            if (i == 0) {
                if (!node.isSource() || !streamGraph.getInEdges(node.getId()).isEmpty()) {
                    throw new UnsupportedOperationException("The first node of a linear graph must be a Source");
                }
            } else {
                List<StreamEdge> incoming = streamGraph.getInEdges(node.getId());
                if (incoming.size() != 1
                        || incoming.getFirst().sourceId() != nodes.get(i - 1).getId()) {
                    throw new UnsupportedOperationException("Multi-input and branching graphs are unsupported");
                }
            }
            if (i == nodes.size() - 1) {
                if (!node.isSink() || !streamGraph.getOutEdges(node.getId()).isEmpty()) {
                    throw new UnsupportedOperationException("The last node of a linear graph must be a Sink");
                }
            } else {
                List<StreamEdge> outgoing = streamGraph.getOutEdges(node.getId());
                if (outgoing.size() != 1
                        || outgoing.getFirst().targetId() != nodes.get(i + 1).getId()) {
                    throw new UnsupportedOperationException("Multiple Sinks and branching data routes are unsupported");
                }
                if (i > 0 && !node.isOperator()) {
                    throw new UnsupportedOperationException(
                            "Intermediate nodes in linear graphs must be one-input operators");
                }
            }
        }
        if (subtasks > MAX_JOB_SUBTASKS) {
            throw new UnsupportedOperationException("Maximum number of subtasks per job is " + MAX_JOB_SUBTASKS);
        }
        int capacity = configuration.get(RuntimeOptions.CHANNEL_CAPACITY);
        if (capacity <= 0 || capacity > MAX_CHANNEL_CAPACITY) {
            throw new IllegalArgumentException(
                    "execution.local-channel.capacity must be between 1 and " + MAX_CHANNEL_CAPACITY + " (inclusive)");
        }
    }

    private void validateCheckpoint() {
        if (!checkpointEnabled()) {
            return;
        }
        String directory = configuration.get(CheckpointingOptions.STATE_DIRECTORY);
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("Checkpointing and recovery require a state directory");
        }
        if (configuration.get(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS) != 1) {
            throw new UnsupportedOperationException("Only one checkpoint may be in progress at a time");
        }
        for (StreamNode sink : streamGraph.getSinkNodes()) {
            if (sink.getSink().orElseThrow() instanceof SupportsWriterState<?, ?> stateful
                    && stateful.getWriterStateSerializer() == null) {
                throw new IllegalArgumentException("Stateful Sink requires a non-null writer state serializer");
            }
        }
        LocalCompletedCheckpointStore.graphSignature(
                streamGraph,
                configuration.get(PipelineOptions.MAX_PARALLELISM),
                configuration.get(CheckpointingOptions.DEFINITION_IDENTITY));
    }

    private boolean checkpointEnabled() {
        return !configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL).isZero()
                || configuration.get(CheckpointingOptions.RESTORE_LATEST);
    }
}
