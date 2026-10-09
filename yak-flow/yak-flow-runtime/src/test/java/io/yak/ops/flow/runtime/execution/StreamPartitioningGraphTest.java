package io.yak.ops.flow.runtime.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import io.yak.ops.flow.runtime.graph.StreamEdge;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import io.yak.ops.flow.runtime.operators.OneInputOperator;
import io.yak.ops.flow.runtime.transformations.OneInputTransformation;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreamPartitioningGraphTest {

    @Test
    void shouldGenerateForwardForEqualParallelismAndRebalanceForUnequalParallelism() {
        SourceTransformation<String> source = source(4);
        SinkTransformation<String> sink = sink(source, 4);
        StreamGraph forward = new StreamGraphGenerator(sink, new Configuration()).generate();
        assertEquals(StreamPartitioning.FORWARD, forward.getStreamEdges().getFirst().partitioning());

        sink.setParallelism(2);
        StreamGraph rebalance = new StreamGraphGenerator(sink, new Configuration()).generate();
        assertEquals(StreamPartitioning.REBALANCE, rebalance.getStreamEdges().getFirst().partitioning());
    }

    @Test
    void shouldPreserveExplicitKeySelectionAndPartitionStrategy() {
        SourceTransformation<String> source = source(4);
        OneInputTransformation<String, String> operator = new OneInputTransformation<>(
                source, "operator", () -> new OneInputOperator<>() {
                    @Override
                    public void processElement(String element, io.yak.ops.core.api.operators.Collector<String> out)
                            throws Exception {
                        out.collect(element);
                    }
                }, String.class, 2);
        operator.keyBy((String row) -> row.substring(0, 1));
        SinkTransformation<String> sink = sink(operator, 3);
        sink.setInputPartitioning(StreamPartitioning.REBALANCE);

        StreamGraph graph = new StreamGraphGenerator(sink, new Configuration()).generate();
        assertEquals(StreamPartitioning.KEYED, graph.getStreamEdges().getFirst().partitioning());
        assertNotNull(graph.getStreamEdges().getFirst().keySelector());
        assertEquals(StreamPartitioning.REBALANCE, graph.getStreamEdges().getLast().partitioning());

        sink.keyBy((String row) -> row.substring(0, 1));
        graph = new StreamGraphGenerator(sink, new Configuration()).generate();
        assertEquals(StreamPartitioning.KEYED, graph.getStreamEdges().getLast().partitioning());
        sink.setInputPartitioning(StreamPartitioning.REBALANCE);
        graph = new StreamGraphGenerator(sink, new Configuration()).generate();
        assertEquals(StreamPartitioning.REBALANCE, graph.getStreamEdges().getLast().partitioning());
    }

    @Test
    void shouldRejectForwardParallelismMismatchAndDuplicatePair() {
        SourceTransformation<String> source = source(4);
        SinkTransformation<String> sink = sink(source, 1);
        StreamGraph graph = new StreamGraphGenerator(sink, new Configuration()).generate();
        int sourceId = source.getId();
        int sinkId = sink.getId();

        assertThrows(IllegalArgumentException.class, () -> new StreamGraph(graph.getStreamNodes(),
                List.of(new StreamEdge(sourceId, sinkId, StreamPartitioning.FORWARD))));
        assertThrows(IllegalArgumentException.class, () -> new StreamGraph(graph.getStreamNodes(),
                List.of(new StreamEdge(sourceId, sinkId, StreamPartitioning.REBALANCE),
                        StreamEdge.keyed(sourceId, sinkId, (String record) -> record))));
        sink.setInputPartitioning(StreamPartitioning.FORWARD);
        assertThrows(IllegalArgumentException.class, () -> new StreamGraphGenerator(sink, new Configuration()).generate());
    }

    @Test
    void shouldRequireTypedKeyByForKeyedStrategy() {
        SourceTransformation<String> source = source(1);
        SinkTransformation<String> sink = sink(source, 1);
        assertThrows(IllegalArgumentException.class, () -> sink.setInputPartitioning(StreamPartitioning.KEYED));
        assertThrows(NullPointerException.class, () -> sink.keyBy(null));
        assertThrows(IllegalArgumentException.class, () -> new StreamEdge(1, 2, StreamPartitioning.KEYED));
        assertThrows(IllegalArgumentException.class, () -> new StreamEdge(1, 2, StreamPartitioning.REBALANCE,
                (io.yak.ops.core.api.operators.KeySelector<String>) row -> row));
    }

    private static SourceTransformation<String> source(int parallelism) {
        return new SourceTransformation<>("source", new NoRunSource(), String.class, parallelism);
    }

    private static SinkTransformation<String> sink(io.yak.ops.core.api.dag.Transformation<String> source, int parallelism) {
        Sink<String> sink = () -> {
            throw new AssertionError("Graph generation must not open a SinkWriter");
        };
        return new SinkTransformation<>(source, "sink", sink, parallelism);
    }

    private record TestSplit(String splitId) implements SourceSplit {}

    private static final class NoRunSource implements Source<String, TestSplit, Integer> {

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> createEnumerator(SplitEnumeratorContext<TestSplit> context) {
            throw new AssertionError("Graph generation must not start Enumerator");
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> restoreEnumerator(
                SplitEnumeratorContext<TestSplit> context, Integer checkpointState) {
            throw new AssertionError("Graph generation must not restore Enumerator");
        }

        @Override
        public SourceReader<String, TestSplit> createReader(SourceReaderContext context) {
            throw new AssertionError("Graph generation must not create Reader");
        }

        @Override
        public SimpleVersionedSerializer<TestSplit> getSplitSerializer() {
            throw new AssertionError("No serialization during graph generation");
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            throw new AssertionError("No serialization during graph generation");
        }
    }
}
