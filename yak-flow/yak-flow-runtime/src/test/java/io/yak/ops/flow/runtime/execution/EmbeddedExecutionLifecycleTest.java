package io.yak.ops.flow.runtime.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
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
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import io.yak.ops.flow.runtime.operators.OneInputOperatorFactory;
import io.yak.ops.flow.runtime.transformations.OneInputTransformation;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EmbeddedExecutionLifecycleTest {

    @Test
    void shouldRunBoundedPipelineWithMultipleOperatorsAndFinalFlush() throws Exception {
        List<String> trace = new CopyOnWriteArrayList<>();
        TestSource source = new TestSource(true, List.of("10", "20"), trace);
        TestSink sink = new TestSink(false, trace);
        AtomicReference<String> operatorThread = new AtomicReference<>();
        SourceTransformation<String> input = new SourceTransformation<>("source", source, String.class);
        OneInputOperatorFactory<String, Integer> parse = () -> new OneInputStreamOperator<>() {
            @Override
            public void open() {
                trace.add("parse-open");
            }

            @Override
            public void processElement(String value, Collector<Integer> output) throws Exception {
                operatorThread.set(Thread.currentThread().getName());
                output.collect(Integer.parseInt(value));
            }

            @Override
            public void finish(Collector<Integer> output) throws Exception {
                trace.add("parse-finish");
                output.collect(0);
            }

            @Override
            public void close() {
                trace.add("parse-close");
            }
        };
        OneInputOperatorFactory<Integer, String> render = () -> new OneInputStreamOperator<>() {
            @Override
            public void open() {
                trace.add("render-open");
            }

            @Override
            public void processElement(Integer value, Collector<String> output) throws Exception {
                output.collect("v" + (value + 1));
            }

            @Override
            public void finish(Collector<String> output) throws Exception {
                trace.add("render-finish");
                output.collect("tail");
            }

            @Override
            public void close() {
                trace.add("render-close");
            }
        };
        OneInputTransformation<String, Integer> parser =
                new OneInputTransformation<>(input, "parse", parse, Integer.class);
        OneInputTransformation<Integer, String> renderer =
                new OneInputTransformation<>(parser, "render", render, String.class);
        Configuration configuration = config(1);
        StreamGraph graph = new StreamGraphGenerator(
                new SinkTransformation<>(renderer, "sink", sink), configuration).generate();

        JobClient job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(5, TimeUnit.SECONDS);
        assertEquals(job.getJobID(), job.getJobExecutionResult().get(5, TimeUnit.SECONDS).getJobID());
        assertEquals(JobStatus.FINISHED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(List.of("v11", "v21", "v1", "tail"), sink.rows);
        assertEquals(1, sink.finalFlushes.get());
        assertEquals(1, sink.closes.get());
        assertTrue(source.readerClosed.get());
        assertTrue(source.enumeratorClosed.get());
        assertEquals(source.readerThread.get(), sink.writerThread.get());
        assertEquals(source.readerThread.get(), operatorThread.get());
        assertTrue(trace.indexOf("render-open") < trace.indexOf("parse-open"));
        assertTrue(trace.indexOf("parse-finish") < trace.indexOf("render-finish"));
        assertTrue(trace.indexOf("render-finish") < trace.indexOf("sink-final-flush"));
        assertTrue(trace.indexOf("source-close") < trace.indexOf("sink-close"));
    }

    @Test
    void shouldSupportDirectSourceSinkAndIndependentJobSubmissions() throws Exception {
        TestSource source = new TestSource(true, List.of("one", "two"), new CopyOnWriteArrayList<>());
        TestSink sink = new TestSink(false, new CopyOnWriteArrayList<>());
        Configuration configuration = config(1);
        StreamGraph graph = graph(source, sink, configuration);
        EmbeddedPipelineExecutor executor = new EmbeddedPipelineExecutor();

        JobClient first = executor.execute(graph, configuration).get(5, TimeUnit.SECONDS);
        first.getJobExecutionResult().get(5, TimeUnit.SECONDS);
        JobClient second = executor.execute(graph, configuration).get(5, TimeUnit.SECONDS);
        second.getJobExecutionResult().get(5, TimeUnit.SECONDS);

        assertNotEquals(first.getJobID(), second.getJobID());
        assertEquals(io.yak.ops.flow.runtime.executiongraph.ExecutionState.FINISHED,
                ((EmbeddedJobClient) first).getExecutionGraph().getJobVertices().getFirst()
                        .getTaskVertices().getFirst().getCurrentExecutionAttempt().getState());
        assertEquals(List.of("one", "two", "one", "two"), sink.rows);
        assertEquals(2, sink.finalFlushes.get());
        assertEquals(2, sink.closes.get());
        assertEquals(2, source.readerCreations.get());
    }

    @Test
    void shouldRejectOversizedOrBranchingTopologiesBeforeCreatingResources() {
        TestSource source = new TestSource(true, List.of("one"), new CopyOnWriteArrayList<>());
        TestSink sink = new TestSink(false, new CopyOnWriteArrayList<>());
        Configuration parallel = config(17);
        StreamGraph graph = graph(source, sink, parallel);

        CompletionException failure = assertThrows(
                CompletionException.class, () -> new EmbeddedPipelineExecutor().execute(graph, parallel).join());
        assertTrue(failure.getCause() instanceof UnsupportedOperationException);

        Configuration single = config(1);
        SourceTransformation<String> input = new SourceTransformation<>("source", source, String.class);
        StreamGraph fork = new StreamGraphGenerator(List.of(
                new SinkTransformation<>(input, "sink-a", sink),
                new SinkTransformation<>(input, "sink-b", sink)), single).generate();
        failure = assertThrows(
                CompletionException.class, () -> new EmbeddedPipelineExecutor().execute(fork, single).join());
        assertTrue(failure.getCause() instanceof UnsupportedOperationException);

        single.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(1));
        failure = assertThrows(CompletionException.class, () -> new EmbeddedPipelineExecutor()
                .execute(graph(source, sink, single), single).join());
        assertTrue(failure.getCause() instanceof IllegalArgumentException);
        assertTrue(failure.getCause().getMessage().contains("状态目录"));
        assertEquals(0, sink.writerCreations.get());
        assertEquals(0, source.readerCreations.get());
    }

    @Test
    void shouldFailAndCloseResourcesWithoutSuccessfulFlush() throws Exception {
        TestSource source = new TestSource(true, List.of("one"), new CopyOnWriteArrayList<>());
        TestSink sink = new TestSink(true, new CopyOnWriteArrayList<>());
        Configuration configuration = config(1);
        JobClient job = new EmbeddedPipelineExecutor().execute(graph(source, sink, configuration), configuration)
                .get(5, TimeUnit.SECONDS);

        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause().getMessage().contains("writer failed"));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, sink.finalFlushes.get());
        assertEquals(1, sink.closes.get());
        assertTrue(source.readerClosed.get());
        assertTrue(source.enumeratorClosed.get());
    }

    @Test
    void shouldCancelUnboundedJobWithoutFinalFlush() throws Exception {
        TestSource source = new TestSource(false, List.of(), new CopyOnWriteArrayList<>());
        TestSink sink = new TestSink(false, new CopyOnWriteArrayList<>());
        Configuration configuration = config(1);
        JobClient job = new EmbeddedPipelineExecutor().execute(graph(source, sink, configuration), configuration)
                .get(5, TimeUnit.SECONDS);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!source.readerStarted.get() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(source.readerStarted.get());
        job.cancel().get(5, TimeUnit.SECONDS);

        assertEquals(JobStatus.CANCELED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertTrue(job.getJobExecutionResult().isCompletedExceptionally());
        assertEquals(0, sink.finalFlushes.get());
        assertEquals(1, sink.closes.get());
        assertTrue(source.readerClosed.get());
        assertTrue(source.enumeratorClosed.get());
    }

    @Test
    void shouldClosePartialOperatorChainWhenOpenFails() throws Exception {
        TestSource source = new TestSource(true, List.of("one"), new CopyOnWriteArrayList<>());
        TestSink sink = new TestSink(false, new CopyOnWriteArrayList<>());
        AtomicBoolean operatorClosed = new AtomicBoolean();
        OneInputOperatorFactory<String, String> failing = () -> new OneInputStreamOperator<>() {
            @Override
            public void open() {
                throw new IllegalStateException("operator open failed");
            }

            @Override
            public void processElement(String value, Collector<String> output) throws Exception {
                output.collect(value);
            }

            @Override
            public void close() {
                operatorClosed.set(true);
            }
        };
        Configuration configuration = config(1);
        SourceTransformation<String> input = new SourceTransformation<>("source", source, String.class);
        OneInputTransformation<String, String> middle =
                new OneInputTransformation<>(input, "failing operator", failing, String.class);
        StreamGraph graph = new StreamGraphGenerator(
                new SinkTransformation<>(middle, "sink", sink), configuration).generate();

        JobClient job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(5, TimeUnit.SECONDS);
        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));

        assertTrue(failure.getCause().getMessage().contains("operator open failed"));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertTrue(operatorClosed.get());
        assertEquals(1, sink.closes.get());
        assertEquals(0, sink.finalFlushes.get());
        assertEquals(0, source.readerCreations.get());
        assertTrue(source.enumeratorClosed.get());
    }

    @Test
    void shouldNotCompleteNormallyWhenFinalFlushFails() throws Exception {
        TestSource source = new TestSource(true, List.of("one"), new CopyOnWriteArrayList<>());
        TestSink sink = new TestSink(false, true, new CopyOnWriteArrayList<>());
        Configuration configuration = config(1);
        JobClient job = new EmbeddedPipelineExecutor().execute(graph(source, sink, configuration), configuration)
                .get(5, TimeUnit.SECONDS);

        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause().getMessage().contains("final flush failed"));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, sink.finalFlushes.get());
        assertEquals(1, sink.closes.get());
        assertTrue(source.readerClosed.get());
    }

    @Test
    void shouldPropagateCoordinatorFailureToMailboxAndReleaseResources() throws Exception {
        TestSource source = new TestSource(true, List.of("one"), new CopyOnWriteArrayList<>(), true);
        TestSink sink = new TestSink(false, new CopyOnWriteArrayList<>());
        Configuration configuration = config(1);
        JobClient job = new EmbeddedPipelineExecutor().execute(graph(source, sink, configuration), configuration)
                .get(5, TimeUnit.SECONDS);

        ExecutionException failure = assertThrows(
                ExecutionException.class, () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause().toString().contains("enumerator request failed"));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, sink.finalFlushes.get());
        assertEquals(1, sink.closes.get());
        assertTrue(source.readerClosed.get());
        assertTrue(source.enumeratorClosed.get());
    }

    private static Configuration config(int parallelism) {
        Configuration configuration = new Configuration();
        configuration.set(CoreOptions.DEFAULT_PARALLELISM, parallelism);
        return configuration;
    }

    private static StreamGraph graph(TestSource source, TestSink sink, Configuration configuration) {
        SourceTransformation<String> input = new SourceTransformation<>("source", source, String.class);
        return new StreamGraphGenerator(new SinkTransformation<>(input, "sink", sink), configuration).generate();
    }

    private record TestSplit(String splitId) implements SourceSplit {}

    private static final class TestSource implements Source<String, TestSplit, Integer> {

        private final boolean bounded;
        private final List<String> data;
        private final List<String> trace;
        private final boolean failRequest;
        private final AtomicInteger readerCreations = new AtomicInteger();
        private final AtomicBoolean readerStarted = new AtomicBoolean();
        private final AtomicBoolean readerClosed = new AtomicBoolean();
        private final AtomicBoolean enumeratorClosed = new AtomicBoolean();
        private final AtomicReference<String> readerThread = new AtomicReference<>();

        private TestSource(boolean bounded, List<String> data, List<String> trace) {
            this(bounded, data, trace, false);
        }

        private TestSource(boolean bounded, List<String> data, List<String> trace, boolean failRequest) {
            this.bounded = bounded;
            this.data = List.copyOf(data);
            this.trace = trace;
            this.failRequest = failRequest;
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
                    if (failRequest) {
                        throw new IllegalStateException("enumerator request failed");
                    }
                    for (String value : data) {
                        context.assignSplit(new TestSplit(value), subtaskId);
                    }
                    if (bounded) {
                        context.signalNoMoreSplits(subtaskId);
                    }
                }

                @Override
                public void addSplitsBack(List<TestSplit> splits, int subtaskId) {
                    throw new AssertionError("PR5 does not recover Reader attempts");
                }

                @Override
                public Integer snapshotState(long checkpointId) {
                    return 0;
                }

                @Override
                public void close() {
                    enumeratorClosed.set(true);
                }
            };
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> restoreEnumerator(
                SplitEnumeratorContext<TestSplit> context, Integer checkpointState) {
            throw new AssertionError("PR5 does not restore Enumerator");
        }

        @Override
        public SourceReader<String, TestSplit> createReader(SourceReaderContext context) {
            readerCreations.incrementAndGet();
            readerThread.set(Thread.currentThread().getName());
            return new SourceReader<>() {
                private final Deque<TestSplit> splits = new ArrayDeque<>();
                private CompletableFuture<Void> available = new CompletableFuture<>();
                private boolean noMoreSplits;

                @Override
                public void start() {
                    readerStarted.set(true);
                    context.sendSplitRequest();
                }

                @Override
                public InputStatus pollNext(ReaderOutput<String> output) throws Exception {
                    if (!splits.isEmpty()) {
                        output.collect(splits.removeFirst().splitId());
                        return InputStatus.MORE_AVAILABLE;
                    }
                    if (noMoreSplits) {
                        return InputStatus.END_OF_INPUT;
                    }
                    available = new CompletableFuture<>();
                    return InputStatus.NOTHING_AVAILABLE;
                }

                @Override
                public CompletableFuture<Void> isAvailable() {
                    return available;
                }

                @Override
                public void addSplits(List<TestSplit> assigned) {
                    splits.addAll(assigned);
                    available.complete(null);
                }

                @Override
                public void notifyNoMoreSplits() {
                    noMoreSplits = true;
                    available.complete(null);
                }

                @Override
                public List<TestSplit> snapshotState(long checkpointId) {
                    return List.copyOf(splits);
                }

                @Override
                public void close() {
                    trace.add("source-close");
                    readerClosed.set(true);
                }
            };
        }

        @Override
        public SimpleVersionedSerializer<TestSplit> getSplitSerializer() {
            return TestSplitSerializers.utf8(TestSplit::splitId, TestSplit::new);
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            throw new AssertionError("PR5 has no global checkpoint");
        }
    }

    private static final class TestSink implements Sink<String> {

        private final boolean failWrites;
        private final boolean failFinalFlush;
        private final List<String> trace;
        private final List<String> rows = new CopyOnWriteArrayList<>();
        private final AtomicInteger finalFlushes = new AtomicInteger();
        private final AtomicInteger writerCreations = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicReference<String> writerThread = new AtomicReference<>();

        private TestSink(boolean failWrites, List<String> trace) {
            this(failWrites, false, trace);
        }

        private TestSink(boolean failWrites, boolean failFinalFlush, List<String> trace) {
            this.failWrites = failWrites;
            this.failFinalFlush = failFinalFlush;
            this.trace = trace;
        }

        @Override
        public SinkWriter<String> createWriter(WriterInitContext context) {
            writerCreations.incrementAndGet();
            writerThread.set(Thread.currentThread().getName());
            return new SinkWriter<>() {
                @Override
                public void write(String value) {
                    if (failWrites) {
                        throw new IllegalStateException("writer failed");
                    }
                    rows.add(value);
                }

                @Override
                public void flush(boolean endOfInput) {
                    if (endOfInput) {
                        if (failFinalFlush) {
                            throw new IllegalStateException("final flush failed");
                        }
                        finalFlushes.incrementAndGet();
                        trace.add("sink-final-flush");
                    }
                }

                @Override
                public void close() {
                    closes.incrementAndGet();
                    trace.add("sink-close");
                }
            };
        }
    }
}
