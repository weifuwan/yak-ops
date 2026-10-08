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
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.graph.StreamGraph;
import io.yak.ops.core.graph.StreamGraphGenerator;
import io.yak.ops.core.graph.StreamNode;
import io.yak.ops.core.transformations.SinkTransformation;
import io.yak.ops.core.transformations.SourceTransformation;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
                IllegalArgumentException.class, () -> CompiledJobPlan.compile(graph, defaultConfig(8)));

        assertTrue(failure.getMessage().contains("默认并行度与提交配置不一致"));
        assertEquals(4, graph.getSourceNodes().getFirst().getParallelism());
    }

    @Test
    void shouldAllowDifferentSubmissionDefaultIfAllNodesUseExplicitParallelism() {
        StreamGraph graph = graph(defaultConfig(4), 2, 3, true, false);
        CompiledJobPlan plan = CompiledJobPlan.compile(graph, defaultConfig(8));

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
        CompiledJobPlan plan = CompiledJobPlan.compile(graph, configuration);

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
        assertThrows(IllegalArgumentException.class, () -> CompiledJobPlan.compile(graphWithoutUids, submit));

        StreamGraph graphWithUids = graph(defaultConfig(2), 2, 2, true, true);
        assertSame(graphWithUids, CompiledJobPlan.compile(graphWithUids, submit).graph());

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
        assertThrows(IllegalArgumentException.class, () -> CompiledJobPlan.compile(graph, configuration));
        configuration.removeConfig(CheckpointingOptions.CHECKPOINTING_INTERVAL);

        configuration.set(CheckpointingOptions.CHECKPOINTING_TIMEOUT, Duration.ZERO);
        assertThrows(IllegalArgumentException.class, () -> CompiledJobPlan.compile(graph, configuration));
        configuration.removeConfig(CheckpointingOptions.CHECKPOINTING_TIMEOUT);

        configuration.set(CheckpointingOptions.MIN_PAUSE_BETWEEN_CHECKPOINTS, Duration.ofMillis(-1));
        assertThrows(IllegalArgumentException.class, () -> CompiledJobPlan.compile(graph, configuration));
        configuration.removeConfig(CheckpointingOptions.MIN_PAUSE_BETWEEN_CHECKPOINTS);

        configuration.set(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS, 0);
        assertThrows(IllegalArgumentException.class, () -> CompiledJobPlan.compile(graph, configuration));
    }

    @Test
    void shouldResolveModeAndRejectBatchForUnboundedSource() {
        Configuration configuration = defaultConfig(1);
        StreamGraph graph = graph(configuration, 1, 1, false, false);
        assertEquals(RuntimeExecutionMode.STREAMING, CompiledJobPlan.compile(graph, configuration).runtimeMode());

        configuration.set(ExecutionOptions.RUNTIME_MODE, RuntimeExecutionMode.BATCH);
        assertThrows(IllegalArgumentException.class, () -> CompiledJobPlan.compile(graph, configuration));
    }

    @Test
    void shouldRejectNonpositiveDefaultEvenForExplicitNodes() {
        StreamGraph graph = graph(defaultConfig(1), 2, 3, true, false);
        assertThrows(IllegalArgumentException.class, () -> CompiledJobPlan.compile(graph, defaultConfig(0)));
    }

    @Test
    void shouldPassOneFrozenPlanToRunner() throws Exception {
        Configuration configuration = defaultConfig(3);
        configuration.set(PipelineOptions.NAME, "submitted");
        StreamGraph graph = graph(configuration, 3, 1, true, false);
        AtomicReference<CompiledJobPlan> received = new AtomicReference<>();
        LocalPipelineExecutor executor = new LocalPipelineExecutor((plan, cancellationRequested) -> received.set(plan));

        JobClient job = executor.execute(graph, configuration).get(5, TimeUnit.SECONDS);
        configuration.set(PipelineOptions.NAME, "modified");
        job.getJobExecutionResult().get(5, TimeUnit.SECONDS);

        CompiledJobPlan plan = received.get();
        assertEquals(job.getJobID(), plan.jobID());
        assertSame(graph, plan.graph());
        assertEquals("submitted", plan.configuration().get(PipelineOptions.NAME));
        assertEquals(RuntimeExecutionMode.BATCH, plan.runtimeMode());
    }

    @Test
    void shouldFailSubmissionWithoutCallingRunnerOnParallelismConflict() {
        StreamGraph graph = graph(defaultConfig(2), Transformation.DEFAULT_PARALLELISM, 1, true, false);
        AtomicBoolean invoked = new AtomicBoolean();
        LocalPipelineExecutor executor = new LocalPipelineExecutor((plan, cancellationRequested) -> invoked.set(true));

        assertThrows(CompletionException.class, () -> executor.execute(graph, defaultConfig(3)).join());
        assertFalse(invoked.get());
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
