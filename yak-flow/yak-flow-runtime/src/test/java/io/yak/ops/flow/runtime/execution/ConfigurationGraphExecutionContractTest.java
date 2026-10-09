package io.yak.ops.flow.runtime.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.flow.runtime.configuration.RuntimeOptions;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.graph.StreamingJobGraphGenerator;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import io.yak.ops.flow.runtime.executiongraph.ExecutionGraph;
import io.yak.ops.flow.runtime.graph.StreamNode;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class ConfigurationGraphExecutionContractTest {

    @Test
    void shouldUseOneDefaultParallelismAndCheckpointOption() {
        assertSame(CoreOptions.DEFAULT_PARALLELISM, ExecutionOptions.DEFAULT_PARALLELISM);
        assertSame(CheckpointingOptions.CHECKPOINTING_INTERVAL, ExecutionOptions.CHECKPOINT_INTERVAL);

        Configuration configuration = new Configuration();
        assertEquals(1, (int) configuration.get(CoreOptions.DEFAULT_PARALLELISM));
        assertEquals(Duration.ZERO, configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
        assertTrue(configuration.getOptional(CheckpointingOptions.CHECKPOINTING_INTERVAL).isEmpty());

        configuration.set(ExecutionOptions.CHECKPOINT_INTERVAL, Duration.ofSeconds(5));
        assertEquals(Duration.ofSeconds(5), configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
    }

    @Test
    void shouldPreserveLocalChannelKeyUnderRuntimeOptions() {
        Configuration configuration = new Configuration();
        assertEquals("execution.local-channel.capacity", RuntimeOptions.CHANNEL_CAPACITY.key());
        assertEquals(64, (int) configuration.get(RuntimeOptions.CHANNEL_CAPACITY));
        configuration.set(RuntimeOptions.CHANNEL_CAPACITY, 192);
        assertEquals(192, (int) configuration.get(RuntimeOptions.CHANNEL_CAPACITY));
    }

    @Test
    void shouldRetainDeclaredAndResolvedParallelismSeparately() {
        StreamGraph graph = graph(defaultConfig(3), Transformation.DEFAULT_PARALLELISM, 5, true, false);
        StreamNode source = graph.getSourceNodes().getFirst();
        StreamNode sink = graph.getSinkNodes().getFirst();

        assertTrue(source.usesDefaultParallelism());
        assertEquals(Transformation.DEFAULT_PARALLELISM, source.getDeclaredParallelism());
        assertEquals(3, source.getParallelism());
        assertFalse(sink.usesDefaultParallelism());
        assertEquals(5, sink.getDeclaredParallelism());
        assertEquals(5, sink.getParallelism());
    }

    @Test
    void shouldRejectSubmissionDefaultThatConflictsWithGeneratedGraph() {
        StreamGraph graph = graph(defaultConfig(4), Transformation.DEFAULT_PARALLELISM, 2, true, false);
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> new StreamingJobGraphGenerator(graph, defaultConfig(8)).generate());

        assertTrue(failure.getMessage().contains("默认并行度与提交配置不一致"));
        assertEquals(4, graph.getSourceNodes().getFirst().getParallelism());
    }

    @Test
    void shouldAllowDifferentSubmissionDefaultIfAllNodesUseExplicitParallelism() {
        StreamGraph graph = graph(defaultConfig(4), 2, 3, true, false);
        JobGraph plan = new StreamingJobGraphGenerator(graph, defaultConfig(8)).generate();

        assertSame(graph, plan.graph());
        assertEquals(2, plan.graph().getSourceNodes().getFirst().getParallelism());
        assertEquals(3, plan.graph().getSinkNodes().getFirst().getParallelism());
        assertEquals(RuntimeExecutionMode.BATCH, plan.runtimeMode());
    }

    @Test
    void shouldNotLeakMutableSubmittedConfiguration() {
        Configuration configuration = defaultConfig(2);
        configuration.set(PipelineOptions.NAME, "first");
        StreamGraph graph = graph(configuration, Transformation.DEFAULT_PARALLELISM, 1, true, false);
        JobGraph plan = new StreamingJobGraphGenerator(graph, configuration).generate();

        configuration.set(PipelineOptions.NAME, "changed");
        configuration.set(ExecutionOptions.RUNTIME_MODE, RuntimeExecutionMode.STREAMING);
        Configuration exposed = plan.configuration();
        exposed.set(PipelineOptions.NAME, "outside");
        exposed.set(CoreOptions.DEFAULT_PARALLELISM, 9);

        assertEquals("first", plan.configuration().get(PipelineOptions.NAME));
        assertEquals(2, (int) plan.configuration().get(CoreOptions.DEFAULT_PARALLELISM));
        assertEquals(RuntimeExecutionMode.BATCH, plan.runtimeMode());
    }

    @Test
    void shouldRequireStableUidsWhenAutomaticIdsAreDisabled() {
        StreamGraph graphWithoutUids = graph(defaultConfig(2), 2, 2, true, false);
        Configuration submit = defaultConfig(2);
        submit.set(PipelineOptions.AUTO_GENERATE_UIDS, false);
        assertThrows(IllegalArgumentException.class, () -> new StreamingJobGraphGenerator(graphWithoutUids, submit).generate());

        StreamGraph graphWithUids = graph(defaultConfig(2), 2, 2, true, true);
        assertSame(graphWithUids, new StreamingJobGraphGenerator(graphWithUids, submit).generate().graph());

        Configuration compileWithMandatoryUids = defaultConfig(2);
        compileWithMandatoryUids.set(PipelineOptions.AUTO_GENERATE_UIDS, false);
        assertThrows(
                IllegalArgumentException.class,
                () -> graph(compileWithMandatoryUids, 2, 2, true, false));
    }

    @Test
    void shouldRejectInvalidCheckpointLimitsBeforeStartingJob() {
        StreamGraph graph = graph(defaultConfig(2), 2, 2, true, false);
        Configuration configuration = defaultConfig(2);

        configuration.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(-1));
        assertThrows(IllegalArgumentException.class, () -> new StreamingJobGraphGenerator(graph, configuration).generate());
        configuration.removeConfig(CheckpointingOptions.CHECKPOINTING_INTERVAL);

        configuration.set(CheckpointingOptions.CHECKPOINTING_TIMEOUT, Duration.ZERO);
        assertThrows(IllegalArgumentException.class, () -> new StreamingJobGraphGenerator(graph, configuration).generate());
        configuration.removeConfig(CheckpointingOptions.CHECKPOINTING_TIMEOUT);

        configuration.set(CheckpointingOptions.MIN_PAUSE_BETWEEN_CHECKPOINTS, Duration.ofMillis(-1));
        assertThrows(IllegalArgumentException.class, () -> new StreamingJobGraphGenerator(graph, configuration).generate());
        configuration.removeConfig(CheckpointingOptions.MIN_PAUSE_BETWEEN_CHECKPOINTS);

        configuration.set(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS, 0);
        assertThrows(IllegalArgumentException.class, () -> new StreamingJobGraphGenerator(graph, configuration).generate());
    }

    @Test
    void shouldResolveModeAndRejectBatchForUnboundedSource() {
        Configuration configuration = defaultConfig(1);
        StreamGraph graph = graph(configuration, 1, 1, false, false);
        assertEquals(RuntimeExecutionMode.STREAMING, new StreamingJobGraphGenerator(graph, configuration).generate().runtimeMode());

        configuration.set(ExecutionOptions.RUNTIME_MODE, RuntimeExecutionMode.BATCH);
        assertThrows(IllegalArgumentException.class, () -> new StreamingJobGraphGenerator(graph, configuration).generate());
    }

    @Test
    void shouldRejectNonpositiveDefaultEvenForExplicitNodes() {
        StreamGraph graph = graph(defaultConfig(1), 2, 3, true, false);
        assertThrows(IllegalArgumentException.class, () -> new StreamingJobGraphGenerator(graph, defaultConfig(0)).generate());
    }

    @Test
    void shouldBuildPhysicalVerticesWithFrozenSubmissionConfiguration() {
        Configuration configuration = defaultConfig(3);
        configuration.set(PipelineOptions.NAME, "submitted");
        StreamGraph graph = graph(configuration, 3, 1, true, false);
        JobGraph jobGraph = new StreamingJobGraphGenerator(graph, configuration).generate();
        ExecutionGraph executionGraph = new ExecutionGraph(jobGraph);
        configuration.set(PipelineOptions.NAME, "modified");

        assertEquals(executionGraph.getJobID(), jobGraph.jobID());
        assertSame(graph, jobGraph.graph());
        assertEquals("submitted", jobGraph.configuration().get(PipelineOptions.NAME));
        assertEquals(RuntimeExecutionMode.BATCH, jobGraph.runtimeMode());
        assertEquals(2, jobGraph.getVertices().size());
        assertEquals(1, jobGraph.getEdges().size());
        assertEquals(3, executionGraph.getJobVertices().getFirst().getTaskVertices().size());
        assertEquals(0, executionGraph.getJobVertices().getFirst().getTaskVertex(0)
                .getCurrentExecutionAttempt().getAttemptNumber());
    }

    @Test
    void shouldChainSingleParallelismIntoOneDeployableJobVertex() {
        Configuration configuration = defaultConfig(1);
        StreamGraph graph = graph(configuration, 1, 1, true, false);
        JobGraph jobGraph = new StreamingJobGraphGenerator(graph, configuration).generate();

        assertEquals(1, jobGraph.getVertices().size());
        assertEquals(0, jobGraph.getEdges().size());
        assertTrue(jobGraph.getVertices().getFirst().isChained());
        assertEquals(2, jobGraph.getVertices().getFirst().getOperators().size());
        assertEquals(1, new ExecutionGraph(jobGraph).getJobVertices().getFirst().getTaskVertices().size());
    }

    @Test
    void shouldFailSubmissionWithoutDeployingWhenParallelismConflicts() {
        StreamGraph graph = graph(defaultConfig(2), Transformation.DEFAULT_PARALLELISM, 1, true, false);
        EmbeddedPipelineExecutor executor = new EmbeddedPipelineExecutor();
        assertThrows(CompletionException.class, () -> executor.execute(graph, defaultConfig(3)).join());
    }

    private static Configuration defaultConfig(int parallelism) {
        Configuration configuration = new Configuration();
        configuration.set(CoreOptions.DEFAULT_PARALLELISM, parallelism);
        return configuration;
    }

    private static StreamGraph graph(
            Configuration configuration,
            int sourceParallelism,
            int sinkParallelism,
            boolean bounded,
            boolean explicitUids) {
        Source<String, TestSplit, Integer> source = new ContractSource(bounded);
        SourceTransformation<String> input =
                new SourceTransformation<>("source", source, String.class, sourceParallelism);
        Sink<String> sink = () -> {
            throw new AssertionError("Graph compilation must not create a SinkWriter");
        };
        SinkTransformation<String> output = new SinkTransformation<>(input, "sink", sink, sinkParallelism);
        if (explicitUids) {
            input.setUid("source-uid");
            output.setUid("sink-uid");
        }
        return new StreamGraphGenerator(output, configuration).generate();
    }

    private record TestSplit(String splitId) implements SourceSplit {}

    private static final class ContractSource implements Source<String, TestSplit, Integer> {

        private final Boundedness boundedness;

        private ContractSource(boolean bounded) {
            boundedness = bounded ? Boundedness.BOUNDED : Boundedness.CONTINUOUS_UNBOUNDED;
        }

        @Override
        public Boundedness getBoundedness() {
            return boundedness;
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> createEnumerator(SplitEnumeratorContext<TestSplit> context) {
            throw new AssertionError("Compile must not open Enumerator");
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> restoreEnumerator(
                SplitEnumeratorContext<TestSplit> context, Integer checkpointState) {
            throw new AssertionError("Compile must not restore Enumerator");
        }

        @Override
        public SourceReader<String, TestSplit> createReader(SourceReaderContext context) {
            throw new AssertionError("Compile must not create Reader");
        }

        @Override
        public SimpleVersionedSerializer<TestSplit> getSplitSerializer() {
            throw new AssertionError("Compile must not serialize Split");
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            throw new AssertionError("Compile must not serialize Checkpoint");
        }
    }
}
