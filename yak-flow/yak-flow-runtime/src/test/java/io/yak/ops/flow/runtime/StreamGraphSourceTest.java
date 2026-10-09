package io.yak.ops.flow.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.graph.StreamNode;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import org.junit.jupiter.api.Test;

class StreamGraphSourceTest {

    @Test
    void shouldRetainTypedSourceDefinitionWithoutCreatingRuntimeInstances() {
        Source<String, TestSplit, Integer> source = new ContractSource();
        SourceTransformation<String> input = new SourceTransformation<>("source", source, String.class);
        Sink<String> sink = context -> {
            throw new AssertionError("Graph construction must not create a SinkWriter");
        };
        SinkTransformation<String> output = new SinkTransformation<>(input, "sink", sink);

        Configuration configuration = new Configuration();
        configuration.set(ExecutionOptions.DEFAULT_PARALLELISM, 2);

        StreamGraph graph = new StreamGraphGenerator(output, configuration).generate();
        StreamNode node = graph.getStreamNode(input.getId());

        assertTrue(node.isSource());
        assertTrue(graph.isBounded());
        assertSame(source, node.getSource().orElseThrow());
        assertEquals(2, node.getParallelism());
    }

    private record TestSplit(String splitId) implements SourceSplit {}

    private static final class ContractSource implements Source<String, TestSplit, Integer> {

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> createEnumerator(SplitEnumeratorContext<TestSplit> context) {
            throw new AssertionError("Graph construction must not start a SplitEnumerator");
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> restoreEnumerator(
                SplitEnumeratorContext<TestSplit> context, Integer checkpointState) {
            throw new AssertionError("Graph construction must not restore a SplitEnumerator");
        }

        @Override
        public SourceReader<String, TestSplit> createReader(SourceReaderContext context) {
            throw new AssertionError("Graph construction must not create a SourceReader");
        }

        @Override
        public SimpleVersionedSerializer<TestSplit> getSplitSerializer() {
            throw new AssertionError("Graph construction must not require Split serialization");
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            throw new AssertionError("Graph construction must not require checkpoint serialization");
        }
    }
}
