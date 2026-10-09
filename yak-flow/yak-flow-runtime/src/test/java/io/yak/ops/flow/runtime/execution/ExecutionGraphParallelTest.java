package io.yak.ops.flow.runtime.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.flow.runtime.support.TestSplitSerializers;
import io.yak.ops.core.api.operators.Collector;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.flow.runtime.configuration.RuntimeOptions;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import io.yak.ops.flow.runtime.operators.OneInputOperator;
import io.yak.ops.flow.runtime.operators.OneInputOperatorFactory;
import io.yak.ops.flow.runtime.transformations.OneInputTransformation;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExecutionGraphParallelTest {

    @Test
    void shouldFanInFourSourceReadersToOneSinkWithoutLosingRecords() throws Exception {
        ParallelSource source = new ParallelSource(true,
                List.of(List.of("s0-1", "s0-2"), List.of("s1-1"), List.of("s2-1"), List.of("s3-1", "s3-2")));
        ParallelSink sink = new ParallelSink(null);
        Configuration configuration = config(1, 2);
        StreamGraph graph = graph(source, 4, sink, 1, configuration);

        assertEquals(StreamPartitioning.REBALANCE, graph.getStreamEdges().getFirst().partitioning());
        JobClient job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(5, TimeUnit.SECONDS);
        assertEquals(job.getJobID(), job.getJobExecutionResult().get(5, TimeUnit.SECONDS).getJobID());
        assertEquals(JobStatus.FINISHED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(4, ((EmbeddedJobClient) job).getExecutionGraph().getJobVertices().getFirst()
                .getTaskVertices().size());
        assertEquals(0, ((EmbeddedJobClient) job).getExecutionGraph().getJobVertices().getFirst()
                .getTaskVertex(0).getCurrentExecutionAttempt().getAttemptNumber());
        assertEquals(io.yak.ops.flow.runtime.executiongraph.ExecutionState.FINISHED,
                ((EmbeddedJobClient) job).getExecutionGraph().getJobVertices().getFirst()
                        .getTaskVertex(0).getCurrentExecutionAttempt().getState());
        assertEquals(4, source.readerCreated.get());
        assertEquals(4, source.readerClosed.get());
        assertEquals(1, sink.writers.size());
        assertEquals(1, sink.finalFlushes.get());
        assertEquals(1, sink.closed.get());
        assertEquals(1, source.enumeratorClosed.get());
        List<String> rows = sink.allRows();
        assertEquals(6, rows.size());
        assertTrue(rows.containsAll(List.of("s0-1", "s0-2", "s1-1", "s2-1", "s3-1", "s3-2")));
    }

    @Test
    void shouldUseIndependentOperatorsAndWritersAcrossDifferentParallelisms() throws Exception {
        ParallelSource source = new ParallelSource(true,
                List.of(List.of("a0", "b0"), List.of("a1", "b1"), List.of("a2", "b2"), List.of("a3", "b3")));
        ParallelSink sink = new ParallelSink(null);
        AtomicInteger createdOperators = new AtomicInteger();
        AtomicInteger finishedOperators = new AtomicInteger();
        AtomicInteger closedOperators = new AtomicInteger();
        OneInputOperatorFactory<String, String> factory = () -> {
            createdOperators.incrementAndGet();
            return new OneInputOperator<>() {
                @Override
                public void processElement(String element, Collector<String> out) throws Exception {
                    out.collect(element.toUpperCase());
                }

                @Override
                public void finish(Collector<String> out) throws Exception {
                    finishedOperators.incrementAndGet();
                    out.collect("TAIL");
                }

                @Override
                public void close() {
                    closedOperators.incrementAndGet();
                }
            };
        };

        Configuration configuration = config(1, 1);
        SourceTransformation<String> input = new SourceTransformation<>("source", source, String.class, 4);
        OneInputTransformation<String, String> middle =
                new OneInputTransformation<>(input, "uppercase", factory, String.class, 3);
        StreamGraph graph = new StreamGraphGenerator(
                new SinkTransformation<>(middle, "sink", sink, 2), configuration).generate();

        JobClient job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(5, TimeUnit.SECONDS);
        job.getJobExecutionResult().get(5, TimeUnit.SECONDS);

        assertEquals(3, createdOperators.get());
        assertEquals(3, finishedOperators.get());
        assertEquals(3, closedOperators.get());
        assertEquals(2, sink.writers.size());
        assertEquals(2, sink.closed.get());
        assertEquals(2, sink.finalFlushes.get());
        assertEquals(11, sink.allRows().size());
        assertEquals(3, Collections.frequency(sink.allRows(), "TAIL"));
        assertTrue(sink.allRows().containsAll(List.of("A0", "B0", "A1", "B1", "A2", "B2", "A3", "B3")));
        assertEquals(4, source.readerClosed.get());
    }

    @Test
    void shouldKeepEqualBusinessKeysInTheSameSinkWriterAcrossReaders() throws Exception {
        ParallelSource source = new ParallelSource(true,
                List.of(List.of("A-0", "B-0"), List.of("A-1", "B-1"), List.of("A-2", "B-2")));
        ParallelSink sink = new ParallelSink(null);
        Configuration configuration = config(1, 2);
        SourceTransformation<String> input = new SourceTransformation<>("source", source, String.class, 3);
        SinkTransformation<String> output = new SinkTransformation<>(input, "sink", sink, 2);
        output.keyBy((String record) -> record.substring(0, 1));
        StreamGraph graph = new StreamGraphGenerator(output, configuration).generate();
        assertEquals(StreamPartitioning.KEYED, graph.getStreamEdges().getFirst().partitioning());

        JobClient job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(5, TimeUnit.SECONDS);
        job.getJobExecutionResult().get(5, TimeUnit.SECONDS);

        assertEquals(2, sink.writers.size());
        assertEquals(6, sink.allRows().size());
        for (List<String> writerRows : sink.writers) {
            assertFalse(writerRows.isEmpty());
            String firstKey = writerRows.getFirst().substring(0, 1);
            assertTrue(writerRows.stream().allMatch(row -> row.startsWith(firstKey)));
        }
        assertNotEquals(sink.writers.getFirst().getFirst().substring(0, 1),
                sink.writers.getLast().getFirst().substring(0, 1));
    }

    @Test
    void shouldRouteForwardWhenUpstreamAndSinkParallelismMatch() throws Exception {
        ParallelSource source = new ParallelSource(true,
                List.of(List.of("s0"), List.of("s1"), List.of("s2")));
        ParallelSink sink = new ParallelSink(null);
        Configuration configuration = config(1, 1);
        StreamGraph graph = graph(source, 3, sink, 3, configuration);
        assertEquals(StreamPartitioning.FORWARD, graph.getStreamEdges().getFirst().partitioning());

        JobClient job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(5, TimeUnit.SECONDS);
        job.getJobExecutionResult().get(5, TimeUnit.SECONDS);
        assertEquals(3, sink.writers.size());
        for (List<String> output : sink.writers) {
            assertEquals(1, output.size());
        }
        assertTrue(sink.allRows().containsAll(List.of("s0", "s1", "s2")));
    }

    @Test
    void shouldFailWholeJobAndCloseOtherTasksWhenSinkFails() throws Exception {
        ParallelSource source = new ParallelSource(true,
                List.of(List.of("a"), List.of("FAIL"), List.of("c"), List.of("d")));
        ParallelSink sink = new ParallelSink("FAIL");
        Configuration configuration = config(1, 1);
        StreamGraph graph = graph(source, 4, sink, 1, configuration);
        JobClient job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(5, TimeUnit.SECONDS);

        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause().toString().contains("writer failed"));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, sink.finalFlushes.get());
        assertEquals(1, sink.closed.get());
        assertEquals(source.readerCreated.get(), source.readerClosed.get());
        assertEquals(1, source.enumeratorClosed.get());
    }

    @Test
    void shouldCancelUnboundedParallelJobWithoutDeadlockingFullChannels() throws Exception {
        ParallelSource source = new ParallelSource(false,
                List.of(List.of("a", "b", "c"), List.of("d", "e", "f"), List.of("g", "h", "i")));
        CountDownLatch writeEntered = new CountDownLatch(1);
        ParallelSink sink = new ParallelSink(null, writeEntered);
        Configuration configuration = config(1, 1);
        StreamGraph graph = graph(source, 3, sink, 1, configuration);
        JobClient job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(5, TimeUnit.SECONDS);

        assertTrue(writeEntered.await(5, TimeUnit.SECONDS));
        job.cancel().get(5, TimeUnit.SECONDS);

        assertEquals(JobStatus.CANCELED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertTrue(job.getJobExecutionResult().isCompletedExceptionally());
        assertEquals(0, sink.finalFlushes.get());
        assertEquals(1, sink.closed.get());
        assertEquals(source.readerCreated.get(), source.readerClosed.get());
        assertEquals(1, source.enumeratorClosed.get());
    }

    @Test
    void shouldRejectOutOfRangeChannelCapacityBeforeCreatingReaders() {
        ParallelSource source = new ParallelSource(true, List.of(List.of("value"), List.of("value2")));
        ParallelSink sink = new ParallelSink(null);
        Configuration configuration = config(1, 0);
        StreamGraph graph = graph(source, 2, sink, 1, configuration);

        CompletionException failure = assertThrows(
                CompletionException.class, () -> new EmbeddedPipelineExecutor().execute(graph, configuration).join());
        assertTrue(failure.getCause() instanceof IllegalArgumentException);
        assertEquals(0, source.readerCreated.get());
        assertEquals(0, sink.writers.size());
    }

    @Test
    void shouldPropagateOperatorFailureAndCloseEveryCreatedInstance() throws Exception {
        ParallelSource source = new ParallelSource(true, List.of(List.of("good"), List.of("BAD")));
        ParallelSink sink = new ParallelSink(null);
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        OneInputOperatorFactory<String, String> failing = () -> new OneInputOperator<>() {
            @Override
            public void open() {
                opened.incrementAndGet();
            }

            @Override
            public void processElement(String record, Collector<String> out) throws Exception {
                if ("BAD".equals(record)) {
                    throw new IllegalStateException("operator processing failed");
                }
                out.collect(record);
            }

            @Override
            public void close() {
                closed.incrementAndGet();
            }
        };
        Configuration configuration = config(1, 1);
        SourceTransformation<String> input = new SourceTransformation<>("source", source, String.class, 2);
        OneInputTransformation<String, String> middle =
                new OneInputTransformation<>(input, "transform", failing, String.class, 2);
        StreamGraph graph = new StreamGraphGenerator(
                new SinkTransformation<>(middle, "sink", sink, 1), configuration).generate();

        JobClient job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(5, TimeUnit.SECONDS);
        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause().toString().contains("operator processing failed"));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(opened.get(), closed.get());
        assertEquals(source.readerCreated.get(), source.readerClosed.get());
        assertEquals(1, sink.closed.get());
        assertEquals(0, sink.finalFlushes.get());
    }

    private static Configuration config(int defaultParallelism, int capacity) {
        Configuration config = new Configuration();
        config.set(CoreOptions.DEFAULT_PARALLELISM, defaultParallelism);
        config.set(RuntimeOptions.CHANNEL_CAPACITY, capacity);
        return config;
    }

    private static StreamGraph graph(
            ParallelSource source, int sourceParallelism, ParallelSink sink,
            int sinkParallelism, Configuration configuration) {
        SourceTransformation<String> input =
                new SourceTransformation<>("source", source, String.class, sourceParallelism);
        return new StreamGraphGenerator(
                new SinkTransformation<>(input, "sink", sink, sinkParallelism), configuration).generate();
    }

    private record TestSplit(String splitId, int readerIndex) implements SourceSplit {}

    private static final class ParallelSource implements Source<String, TestSplit, Integer> {

        private final boolean bounded;
        private final List<List<String>> byReader;
        private final AtomicInteger readerCreated = new AtomicInteger();
        private final AtomicInteger readerClosed = new AtomicInteger();
        private final AtomicInteger enumeratorClosed = new AtomicInteger();

        private ParallelSource(boolean bounded, List<List<String>> byReader) {
            this.bounded = bounded;
            this.byReader = byReader;
        }

        @Override
        public Boundedness getBoundedness() {
            return bounded ? Boundedness.BOUNDED : Boundedness.CONTINUOUS_UNBOUNDED;
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> createEnumerator(SplitEnumeratorContext<TestSplit> context) {
            return new SplitEnumerator<>() {
                @Override
                public void start() {}

                @Override
                public void addReader(int subtaskId) {}

                @Override
                public void handleSplitRequest(int subtaskId) {
                    context.assignSplit(new TestSplit("split-" + subtaskId, subtaskId), subtaskId);
                    if (bounded) {
                        context.signalNoMoreSplits(subtaskId);
                    }
                }

                @Override
                public void addSplitsBack(List<TestSplit> splits, int subtaskId) {
                    throw new AssertionError("No checkpoint recovery in this test");
                }

                @Override
                public Integer snapshotState(long checkpointId) {
                    return 0;
                }

                @Override
                public void close() {
                    enumeratorClosed.incrementAndGet();
                }
            };
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> restoreEnumerator(
                SplitEnumeratorContext<TestSplit> context, Integer checkpointState) {
            throw new AssertionError("No checkpoint recovery in this test");
        }

        @Override
        public SourceReader<String, TestSplit> createReader(SourceReaderContext context) {
            readerCreated.incrementAndGet();
            return new SourceReader<>() {

                private final Deque<String> records = new ArrayDeque<>();
                private CompletableFuture<Void> availability = new CompletableFuture<>();
                private boolean noMoreSplits;

                @Override
                public void start() {
                    context.sendSplitRequest();
                }

                @Override
                public InputStatus pollNext(ReaderOutput<String> output) throws Exception {
                    if (!records.isEmpty()) {
                        output.collect(records.removeFirst());
                        return InputStatus.MORE_AVAILABLE;
                    }
                    if (noMoreSplits) {
                        return InputStatus.END_OF_INPUT;
                    }
                    availability = new CompletableFuture<>();
                    return InputStatus.NOTHING_AVAILABLE;
                }

                @Override
                public CompletableFuture<Void> isAvailable() {
                    return availability;
                }

                @Override
                public void addSplits(List<TestSplit> splits) {
                    for (TestSplit split : splits) {
                        assertEquals(context.getIndexOfSubtask(), split.readerIndex());
                        records.addAll(byReader.get(split.readerIndex()));
                    }
                    availability.complete(null);
                }

                @Override
                public void notifyNoMoreSplits() {
                    noMoreSplits = true;
                    availability.complete(null);
                }

                @Override
                public List<TestSplit> snapshotState(long checkpointId) {
                    return List.of();
                }

                @Override
                public void close() {
                    readerClosed.incrementAndGet();
                }
            };
        }

        @Override
        public SimpleVersionedSerializer<TestSplit> getSplitSerializer() {
            return TestSplitSerializers.utf8(
                    split -> split.splitId() + "|" + split.readerIndex(),
                    value -> {
                        String[] parts = value.split("\\|", 2);
                        return new TestSplit(parts[0], Integer.parseInt(parts[1]));
                    });
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            throw new AssertionError("No global checkpoint state serialization");
        }
    }

    private static final class ParallelSink implements Sink<String> {

        private final String failOnValue;
        private final CountDownLatch holdWrite;
        private final List<List<String>> writers = new CopyOnWriteArrayList<>();
        private final AtomicInteger finalFlushes = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();

        private ParallelSink(String failOnValue) {
            this(failOnValue, null);
        }

        private ParallelSink(String failOnValue, CountDownLatch holdWrite) {
            this.failOnValue = failOnValue;
            this.holdWrite = holdWrite;
        }

        private List<String> allRows() {
            List<String> combined = new ArrayList<>();
            for (List<String> rows : writers) {
                combined.addAll(rows);
            }
            return combined;
        }

        @Override
        public SinkWriter<String> createWriter() {
            List<String> rows = new CopyOnWriteArrayList<>();
            writers.add(rows);
            return new SinkWriter<>() {
                @Override
                public void write(String record) throws Exception {
                    if (holdWrite != null) {
                        holdWrite.countDown();
                        // 模拟 Sink 阻塞；Job 取消应中断当前虚拟线程。
                        new CountDownLatch(1).await();
                    }
                    if (record.equals(failOnValue)) {
                        throw new IllegalStateException("writer failed: " + record);
                    }
                    rows.add(record);
                }

                @Override
                public void flush(boolean endOfInput) {
                    if (endOfInput) {
                        finalFlushes.incrementAndGet();
                    }
                }

                @Override
                public void close() {
                    closed.incrementAndGet();
                }
            };
        }
    }
}
