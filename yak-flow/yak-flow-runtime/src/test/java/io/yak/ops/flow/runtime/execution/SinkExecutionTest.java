package io.yak.ops.flow.runtime.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.StatefulSinkWriter;
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
import io.yak.ops.core.api.operators.Collector;
import io.yak.ops.flow.runtime.checkpoint.CheckpointDeclinedException;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.flow.runtime.graph.StreamEdge;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.graph.StreamNode;
import io.yak.ops.flow.runtime.io.RecordWriterOutput;
import io.yak.ops.flow.runtime.io.partition.InputGate;
import io.yak.ops.flow.runtime.io.partition.ResultPartition;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import io.yak.ops.flow.runtime.operators.OperatorChain;
import io.yak.ops.flow.runtime.operators.sink.SinkWriterOperator;
import io.yak.ops.flow.runtime.support.TestSplitSerializers;
import io.yak.ops.flow.runtime.tasks.OneInputStreamTask;
import io.yak.ops.flow.runtime.transformations.OneInputTransformation;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SinkExecutionTest {

    @Test
    void shouldProvideSinkWriterContextInChainedOperatorAndPreserveWriterLifecycle() throws Exception {
        Configuration configuration = configuration(1, 256);
        SourceTransformation<String> source = new SourceTransformation<>(
                "source", new SplitSource(), String.class, 1);
        OneInputTransformation<String, String> map = new OneInputTransformation<>(
                source, "map", () -> new OneInputStreamOperator<>() {
                    @Override
                    public void processElement(String value, Collector<String> output) throws Exception {
                        output.collect(value.toUpperCase());
                    }
                }, String.class, 1);
        RecordingSink sink = new RecordingSink();
        SinkTransformation<String> output = new SinkTransformation<>(map, "sink", sink, 1);
        StreamGraph graph = new StreamGraphGenerator(output, configuration).generate();
        var job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(5, TimeUnit.SECONDS);
        job.getJobExecutionResult().get(5, TimeUnit.SECONDS);

        assertEquals(List.of("ROW-0"), sink.rows);
        assertEquals(1, sink.created.size());
        assertEquals(output.getId(), sink.operatorIds.getFirst());
        assertEquals(0, sink.created.getFirst().getTaskInfo().getIndexOfThisSubtask());
        assertEquals(0, sink.created.getFirst().getTaskInfo().getAttemptNumber());
        assertEquals(256, sink.created.getFirst().getTaskInfo().getMaxNumberOfParallelSubtasks());
        assertEquals(1, sink.finalFlushes.get());
        assertEquals(1, sink.closed.get());
    }

    @Test
    void shouldUseOneInputStreamTaskForParallelSinkWriterOperators() throws Exception {
        Configuration configuration = configuration(2, 256);
        SourceTransformation<String> source = new SourceTransformation<>(
                "source", new SplitSource(), String.class, 2);
        RecordingSink sink = new RecordingSink();
        SinkTransformation<String> output = new SinkTransformation<>(source, "sink", sink, 2);
        StreamGraph graph = new StreamGraphGenerator(output, configuration).generate();
        var job = new EmbeddedPipelineExecutor().execute(graph, configuration).get(5, TimeUnit.SECONDS);
        job.getJobExecutionResult().get(5, TimeUnit.SECONDS);

        assertTrue(sink.rows.containsAll(List.of("row-0", "row-1")));
        assertEquals(2, sink.rows.size());
        assertEquals(2, sink.created.size());
        assertEquals(2, sink.finalFlushes.get());
        assertEquals(2, sink.closed.get());
        assertTrue(sink.created.stream().allMatch(ctx -> ctx.getTaskInfo().getNumberOfParallelSubtasks() == 2));
        assertTrue(sink.created.stream().allMatch(ctx -> ctx.getTaskInfo().getMaxNumberOfParallelSubtasks() == 256));
        assertTrue(sink.operatorIds.stream().allMatch(id -> id == output.getId()));
    }


    @Test
    void intermediateOperatorMustTreatCheckpointDeclineAtEofAsNonFatal() throws Exception {
        SourceTransformation<String> source = new SourceTransformation<>(
                "source", new SplitSource(), String.class, 2);
        AtomicInteger finishes = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        OneInputTransformation<String, String> transform = new OneInputTransformation<>(
                source, "middle", () -> new OneInputStreamOperator<>() {
                    @Override
                    public void processElement(String record, Collector<String> output) throws Exception {
                        output.collect(record);
                    }

                    @Override
                    public void finish(Collector<String> output) throws Exception {
                        finishes.incrementAndGet();
                        output.collect("tail");
                    }

                    @Override
                    public void close() {
                        closes.incrementAndGet();
                    }
                }, String.class, 1);
        StreamNode node = StreamNode.fromTransformation(transform, 1);
        InputGate<Object> input = new InputGate<>(8, 2);
        ResultPartition<Object> first = new ResultPartition<>(0, List.of(input));
        ResultPartition<Object> second = new ResultPartition<>(1, List.of(input));
        InputGate<Object> downstream = new InputGate<>(8, 1);
        RecordWriterOutput<Object> output = new RecordWriterOutput<>(
                new StreamEdge(node.getId(), node.getId() + 1), 0, 1,
                new ResultPartition<>(0, List.of(downstream)));
        TaskEnvironment environment = new TaskEnvironment(
                new RuntimeTaskInfo(JobID.generate(), node.getId(), 0, 1, 0, 128), configuration(1, 128));
        OneInputStreamTask task = new OneInputStreamTask(node, environment, input, output);

        try {
            task.start().get(5, TimeUnit.SECONDS);
            CompletableFuture<Map<String, io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot.SerializedState>>
                    checkpoint = task.expectCheckpoint(7);
            first.broadcastBarrier(7);
            second.emitRecord(0, "before-eof");
            second.finish(); // Producer ends without the matching barrier; only CP-7 is declined.
            first.emitRecord(0, "after-decline");
            first.finish();

            ExecutionException declined = assertThrows(
                    ExecutionException.class, () -> checkpoint.get(5, TimeUnit.SECONDS));
            assertTrue(declined.getCause() instanceof CheckpointDeclinedException);
            List<Object> records = new java.util.ArrayList<>();
            while (true) {
                InputStatus status = downstream.emitNext(records::add);
                if (status == InputStatus.END_OF_INPUT) {
                    break;
                }
                if (status == InputStatus.NOTHING_AVAILABLE) {
                    downstream.getAvailableFuture().get(5, TimeUnit.SECONDS);
                }
                assertTrue(records.size() <= 3);
            }
            task.completionFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, finishes.get());
            assertEquals(1, closes.get());
            assertEquals(3, records.size());
            assertEquals("tail", records.getLast());
            assertTrue(records.subList(0, 2).containsAll(List.of("before-eof", "after-decline")));
        } finally {
            if (!task.completionFuture().isDone()) {
                task.cancelAsync().get(5, TimeUnit.SECONDS);
            }
        }
    }


    @Test
    void inlineOperatorFinishFailureMustNotReplayFinalOutput() throws Exception {
        SourceTransformation<String> source = new SourceTransformation<>(
                "source", new SplitSource(), String.class, 1);
        AtomicInteger finishes = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        OneInputTransformation<String, String> transform = new OneInputTransformation<>(
                source, "middle", () -> new OneInputStreamOperator<>() {
                    @Override
                    public void processElement(String record, Collector<String> output) throws Exception {
                        output.collect(record);
                    }

                    @Override
                    public void finish(Collector<String> output) throws Exception {
                        finishes.incrementAndGet();
                        output.collect("tail");
                        throw new IllegalStateException("operator finish failed");
                    }

                    @Override
                    public void close() {
                        closes.incrementAndGet();
                    }
                }, String.class, 1);
        RecordingSink sink = new RecordingSink();
        SinkTransformation<String> sinkTransformation = new SinkTransformation<>(
                transform, "sink", sink, 1);
        OperatorChain chain = new OperatorChain(
                List.of(StreamNode.fromTransformation(transform, 1)),
                StreamNode.fromTransformation(sinkTransformation, 1));
        TaskEnvironment environment = new TaskEnvironment(
                new RuntimeTaskInfo(JobID.generate(), source.getId(), 0, 1, 0, 128), configuration(1, 128));

        try {
            chain.open(environment);
            chain.collect("head");
            IllegalStateException failure = assertThrows(IllegalStateException.class, chain::finish);
            assertEquals("operator finish failed", failure.getMessage());
            IllegalStateException repeated = assertThrows(IllegalStateException.class, chain::finish);
            assertTrue(repeated.getMessage().contains("cannot finish again"));
        } finally {
            chain.close();
        }
        assertEquals(1, finishes.get());
        assertEquals(1, closes.get());
        assertEquals(List.of("head", "tail"), sink.rows);
        assertEquals(0, sink.finalFlushes.get());
        assertEquals(1, sink.closed.get());
    }

    @Test
    void shouldKeepOneInputTaskAliveAfterLogicalEndUntilPhysicalInputAndOutputComplete() throws Exception {
        SourceTransformation<String> source = new SourceTransformation<>(
                "source", new SplitSource(), String.class, 2);
        AtomicInteger finishes = new AtomicInteger();
        OneInputTransformation<String, String> transform = new OneInputTransformation<>(
                source, "middle", () -> new OneInputStreamOperator<>() {
                    @Override
                    public void processElement(String record, Collector<String> output) throws Exception {
                        output.collect(record);
                    }

                    @Override
                    public void finish(Collector<String> output) throws Exception {
                        finishes.incrementAndGet();
                        output.collect("tail");
                    }
                }, String.class, 1);
        StreamNode node = StreamNode.fromTransformation(transform, 1);
        InputGate<Object> input = new InputGate<>(8, 2);
        ResultPartition<Object> first = new ResultPartition<>(0, List.of(input));
        ResultPartition<Object> second = new ResultPartition<>(1, List.of(input));
        InputGate<Object> downstream = new InputGate<>(8, 1);
        RecordWriterOutput<Object> output = new RecordWriterOutput<>(
                new StreamEdge(node.getId(), node.getId() + 1), 0, 1,
                new ResultPartition<>(0, List.of(downstream)));
        TaskEnvironment environment = new TaskEnvironment(
                new RuntimeTaskInfo(JobID.generate(), node.getId(), 0, 1, 0, 128), configuration(1, 128));
        OneInputStreamTask task = new OneInputStreamTask(node, environment, input, output);

        try {
            task.start().get(5, TimeUnit.SECONDS);
            first.emitRecord(0, "first");
            second.emitRecord(0, "second");
            CompletableFuture<Void> firstEnd = first.endData();
            CompletableFuture<Void> secondEnd = second.endData();
            firstEnd.get(5, TimeUnit.SECONDS);
            secondEnd.get(5, TimeUnit.SECONDS);

            assertTrue(task.hasReachedEndOfData());
            assertEquals(1, finishes.get());
            assertFalse(task.completionFuture().isDone());
            ExecutionException declined = assertThrows(
                    ExecutionException.class, () -> task.expectCheckpoint(11).get(5, TimeUnit.SECONDS));
            assertTrue(declined.getCause() instanceof CheckpointDeclinedException);

            // Control events remain legal after EndOfData, but a finished Operator cannot snapshot.
            first.broadcastBarrier(11);
            second.broadcastBarrier(11);
            first.finish();
            second.finish();
            assertFalse(task.completionFuture().isDone(), "Physical finish waits for downstream EndOfData ACK");

            List<Object> rows = new java.util.ArrayList<>();
            assertEquals(InputStatus.MORE_AVAILABLE, downstream.emitNext(rows::add));
            assertEquals(InputStatus.MORE_AVAILABLE, downstream.emitNext(rows::add));
            assertEquals(InputStatus.MORE_AVAILABLE, downstream.emitNext(rows::add));
            assertEquals(InputStatus.MORE_AVAILABLE, downstream.emitNext(rows::add));
            assertEquals(3, rows.size());
            assertEquals("tail", rows.getLast());
            assertTrue(rows.subList(0, 2).containsAll(List.of("first", "second")));

            task.completionFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, finishes.get());
            assertEquals(InputStatus.END_OF_INPUT, downstream.emitNext(rows::add));
        } finally {
            if (!task.completionFuture().isDone()) {
                task.cancelAsync().get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void shouldRefuseStatefulWriterCheckpointWithoutStatePersistence() throws Exception {
        Configuration configuration = configuration(1, 128);
        configuration.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(1));
        AtomicInteger closes = new AtomicInteger();
        Sink<String> sink = context -> new StatefulSinkWriter<String, String>() {
            @Override
            public void write(String value, Context context) {}

            @Override
            public void flush(boolean endOfInput) {}

            @Override
            public List<String> snapshotState(long checkpointId) {
                return List.of("pending");
            }

            @Override
            public void close() {
                closes.incrementAndGet();
            }
        };
        TaskEnvironment environment = new TaskEnvironment(
                new RuntimeTaskInfo(JobID.generate(), 4, 0, 1, 0, 128), configuration);
        SinkWriterOperator<String> operator = new SinkWriterOperator<>(sink, environment);
        assertThrows(UnsupportedOperationException.class, operator::open);
        operator.close();
        assertEquals(1, closes.get());
    }

    private static Configuration configuration(int parallelism, int maxParallelism) {
        Configuration config = new Configuration();
        config.set(CoreOptions.DEFAULT_PARALLELISM, parallelism);
        config.set(PipelineOptions.MAX_PARALLELISM, maxParallelism);
        return config;
    }

    private record Split(String splitId) implements SourceSplit {}

    private static final class SplitSource implements Source<String, Split, Integer> {

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }

        @Override
        public SplitEnumerator<Split, Integer> createEnumerator(SplitEnumeratorContext<Split> context) {
            return new SplitEnumerator<>() {
                @Override
                public void start() {}

                @Override
                public void handleSplitRequest(int subtask) {
                    context.assignSplit(new Split("row-" + subtask), subtask);
                    context.signalNoMoreSplits(subtask);
                }

                @Override
                public void addReader(int subtask) {}

                @Override
                public void addSplitsBack(List<Split> splits, int subtask) {
                    throw new AssertionError("No split recovery without checkpoint");
                }

                @Override
                public Integer snapshotState(long checkpointId) {
                    return 0;
                }

                @Override
                public void close() {}
            };
        }

        @Override
        public SplitEnumerator<Split, Integer> restoreEnumerator(
                SplitEnumeratorContext<Split> context, Integer state) {
            throw new AssertionError("Unexpected state restore");
        }

        @Override
        public SourceReader<String, Split> createReader(SourceReaderContext context) {
            return new SourceReader<>() {
                private final Deque<Split> assigned = new ArrayDeque<>();
                private CompletableFuture<Void> availability = new CompletableFuture<>();
                private boolean noMoreSplits;

                @Override
                public void start() {
                    context.sendSplitRequest();
                }

                @Override
                public InputStatus pollNext(ReaderOutput<String> output) throws Exception {
                    if (!assigned.isEmpty()) {
                        output.collect(assigned.removeFirst().splitId());
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
                public void addSplits(List<Split> splits) {
                    assigned.addAll(splits);
                    availability.complete(null);
                }

                @Override
                public void notifyNoMoreSplits() {
                    noMoreSplits = true;
                    availability.complete(null);
                }

                @Override
                public List<Split> snapshotState(long checkpointId) {
                    return List.copyOf(assigned);
                }

                @Override
                public void close() {}
            };
        }

        @Override
        public SimpleVersionedSerializer<Split> getSplitSerializer() {
            return TestSplitSerializers.utf8(Split::splitId, Split::new);
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            throw new AssertionError("No checkpoint enabled");
        }
    }

    private static final class RecordingSink implements Sink<String> {
        private final List<String> rows = new CopyOnWriteArrayList<>();
        private final List<WriterInitContext> created = new CopyOnWriteArrayList<>();
        private final List<Integer> operatorIds = new CopyOnWriteArrayList<>();
        private final AtomicInteger finalFlushes = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();

        @Override
        public SinkWriter<String> createWriter(WriterInitContext context) {
            created.add(context);
            // Type-specific RuntimeTaskInfo identity is available without leaking it through Core.
            operatorIds.add(((RuntimeTaskInfo) context.getTaskInfo()).operatorId());
            return new SinkWriter<>() {
                @Override
                public void write(String value, Context recordContext) {
                    assertEquals(null, recordContext.timestamp());
                    assertEquals(Long.MIN_VALUE, recordContext.currentWatermark());
                    rows.add(value);
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
