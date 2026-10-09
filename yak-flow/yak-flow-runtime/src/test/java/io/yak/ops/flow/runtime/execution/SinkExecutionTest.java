package io.yak.ops.flow.runtime.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import io.yak.ops.flow.runtime.operators.sink.SinkWriterOperator;
import io.yak.ops.flow.runtime.support.TestSplitSerializers;
import io.yak.ops.flow.runtime.transformations.OneInputTransformation;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
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
    void shouldRefuseStatefulWriterCheckpointWithoutStatePersistence() throws Exception {
        Configuration configuration = configuration(1, 128);
        configuration.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(1));
        AtomicInteger closes = new AtomicInteger();
        Sink<String> sink = context -> new StatefulSinkWriter<String, String>() {
            @Override
            public void write(String value) {}

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
                public void write(String value) {
                    throw new AssertionError("Sink V2 context-aware write was bypassed");
                }

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
