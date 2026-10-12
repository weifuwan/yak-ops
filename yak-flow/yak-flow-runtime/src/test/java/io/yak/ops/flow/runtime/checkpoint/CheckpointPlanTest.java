package io.yak.ops.flow.runtime.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.graph.StreamingJobGraphGenerator;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CheckpointPlanTest {

    @Test
    void allRunningTasksTriggerAtSourceAndWaitForDownstream() throws Exception {
        CheckpointPlan plan = CheckpointPlan.calculate(1, graph(), Set.of());
        assertEquals(Set.of(source(0), source(1)), Set.copyOf(plan.triggerTasks()));
        assertEquals(Set.of(source(0), source(1), sink(0), sink(1)), plan.tasksToAcknowledge());
        assertTrue(plan.fullyFinishedOperators().isEmpty());
        assertTrue(plan.finishedSubtasks(1, Set.of(), Map.of()).isEmpty());
    }

    @Test
    void fullyFinishedSourceMovesCheckpointRootsToRunningSink() throws Exception {
        CheckpointPlan plan = CheckpointPlan.calculate(2, graph(), Set.of(source(0), source(1)));
        assertEquals(Set.of("source-uid"), plan.fullyFinishedOperators());
        assertEquals(Set.of(sink(0), sink(1)), Set.copyOf(plan.triggerTasks()));
        assertEquals(Set.of(sink(0), sink(1)), plan.tasksToAcknowledge());
        assertEquals(Set.of(sink(0), sink(1)),
                plan.finishedSubtasks(2, Set.of(sink(0), sink(1)), Map.of()));
    }

    @Test
    void partialPhysicalOrLogicalCompletionDeclinesInsteadOfInventingState() throws Exception {
        assertThrows(CheckpointDeclinedException.class,
                () -> CheckpointPlan.calculate(3, graph(), Set.of(source(0))));
        CheckpointPlan running = CheckpointPlan.calculate(3, graph(), Set.of());
        assertThrows(CheckpointDeclinedException.class,
                () -> running.finishedSubtasks(3, Set.of(sink(0)), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> running.finishedSubtasks(3, Set.of(sink(0)), Map.of(sink(0), Map.of())));
    }

    @Test
    void inconsistentOrFullyTerminatedGraphCannotStartAnotherCheckpoint() throws Exception {
        assertThrows(CheckpointDeclinedException.class,
                () -> CheckpointPlan.calculate(4, graph(), Set.of(sink(0), sink(1))));
        assertThrows(CheckpointDeclinedException.class,
                () -> CheckpointPlan.calculate(4, graph(), Set.of(source(0), source(1), sink(0), sink(1))));
        assertThrows(IllegalArgumentException.class,
                () -> CheckpointPlan.calculate(4, graph(), Set.of(new CheckpointSnapshot.OperatorSubtask("other", 0))));
    }

    private static CheckpointSnapshot.OperatorSubtask source(int subtask) {
        return new CheckpointSnapshot.OperatorSubtask("source-uid", subtask);
    }

    private static CheckpointSnapshot.OperatorSubtask sink(int subtask) {
        return new CheckpointSnapshot.OperatorSubtask("sink-uid", subtask);
    }

    private static JobGraph graph() {
        SourceTransformation<String> source =
                new SourceTransformation<>("source", new NoRuntimeSource(), String.class, 2);
        Sink<String> sink = context -> {
            throw new AssertionError("Planning must not create a Sink Writer");
        };
        SinkTransformation<String> target = new SinkTransformation<>(source, "sink", sink, 2);
        source.setUid("source-uid");
        target.setUid("sink-uid");
        Configuration config = new Configuration();
        return new StreamingJobGraphGenerator(new StreamGraphGenerator(target, config).generate(), config)
                .generate();
    }

    private record Split(String splitId) implements SourceSplit {}

    private static final class NoRuntimeSource implements Source<String, Split, Integer> {

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public SplitEnumerator<Split, Integer> createEnumerator(SplitEnumeratorContext<Split> context) {
            throw new AssertionError("Planning must not start an Enumerator");
        }

        @Override
        public SplitEnumerator<Split, Integer> restoreEnumerator(
                SplitEnumeratorContext<Split> context, Integer state) {
            throw new AssertionError("Planning must not restore an Enumerator");
        }

        @Override
        public SourceReader<String, Split> createReader(SourceReaderContext context) {
            throw new AssertionError("Planning must not start a Reader");
        }

        @Override
        public SimpleVersionedSerializer<Split> getSplitSerializer() {
            throw new AssertionError("Planning must not serialize Split state");
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            throw new AssertionError("Planning must not serialize Enumerator state");
        }
    }
}
