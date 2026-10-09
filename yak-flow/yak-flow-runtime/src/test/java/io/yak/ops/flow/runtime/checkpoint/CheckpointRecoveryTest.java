package io.yak.ops.flow.runtime.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.yak.ops.core.api.operators.Collector;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.flow.runtime.execution.EmbeddedJobClient;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.operators.OneInputOperator;
import io.yak.ops.flow.runtime.transformations.OneInputTransformation;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckpointRecoveryTest {

    @TempDir
    Path checkpointDirectory;

    @Test
    void shouldCheckpointTwoSourceReadersFlushSinkThenRestoreFromSavedOffsets() throws Exception {
        Configuration firstConfig = configuration(false);
        CapturedSink initialSink = new CapturedSink();
        JobClient first = new EmbeddedPipelineExecutor().execute(
                graph(new OffsetSource(4), initialSink), firstConfig).get(5, TimeUnit.SECONDS);

        awaitCount(initialSink.rows, 8);
        CheckpointSnapshot saved = ((EmbeddedJobClient) first).checkpoint().get(5, TimeUnit.SECONDS);
        assertEquals(1, saved.checkpointId());
        assertEquals(2, saved.readerSplits().size());
        assertEquals(1, initialSink.checkpointFlushes.get());
        assertEquals(8, initialSink.rows.size());
        assertFalse(saved.graphSignature().isBlank());

        first.cancel().get(5, TimeUnit.SECONDS);
        assertEquals(JobStatus.CANCELED, first.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, initialSink.finalFlushes.get());

        // 使用新的 Source/JobID 与 Connector 实例，仅根据 UID/Graph 和稳定二进制 State 恢复。
        CapturedSink resumedSink = new CapturedSink();
        Configuration restoreConfig = configuration(true);
        JobClient restored = new EmbeddedPipelineExecutor().execute(
                graph(new OffsetSource(6), resumedSink), restoreConfig).get(5, TimeUnit.SECONDS);
        awaitCount(resumedSink.rows, 4);
        Set<String> actual = new HashSet<>(resumedSink.rows);
        assertEquals(Set.of("stream-0-4", "stream-0-5", "stream-1-4", "stream-1-5"), actual);
        assertEquals(4, resumedSink.rows.size());
        restored.cancel().get(5, TimeUnit.SECONDS);
        assertEquals(JobStatus.CANCELED, restored.getJobStatus().get(5, TimeUnit.SECONDS));
    }

    @Test
    void shouldRequireStateDirectoryAndStableUidForCheckpoint() {
        CapturedSink sink = new CapturedSink();
        Configuration config = configuration(false);
        config.removeConfig(CheckpointingOptions.STATE_DIRECTORY);
        assertThrows(CompletionException.class,
                () -> new EmbeddedPipelineExecutor().execute(graph(new OffsetSource(2), sink), config).join());

        Configuration withoutUids = configuration(false);
        SourceTransformation<String> input = new SourceTransformation<>(
                "source", new OffsetSource(2), String.class, 1);
        StreamGraph graph = new StreamGraphGenerator(new SinkTransformation<>(
                input, "sink", sink, 1), withoutUids).generate();
        assertThrows(CompletionException.class,
                () -> new EmbeddedPipelineExecutor().execute(graph, withoutUids).join());
        assertEquals(0, sink.createdWriters.get());
    }

    @Test
    void shouldRejectCheckpointForOperatorWithoutPersistedStateContract() {
        CapturedSink sink = new CapturedSink();
        Configuration config = configuration(false);
        SourceTransformation<String> input = new SourceTransformation<>(
                "source", new OffsetSource(2), String.class, 2);
        OneInputTransformation<String, String> operator = new OneInputTransformation<>(
                input, "stateful", () -> new OneInputOperator<>() {
                    private int seen;

                    @Override
                    public void processElement(String element, Collector<String> output) throws Exception {
                        seen++;
                        output.collect(element + seen);
                    }
                }, String.class);
        SinkTransformation<String> end = new SinkTransformation<>(operator, "sink", sink, 1);
        input.setUid("stable-source");
        operator.setUid("stateful-operator");
        end.setUid("stable-sink");
        StreamGraph graph = new StreamGraphGenerator(end, config).generate();
        CompletionException failure = assertThrows(CompletionException.class,
                () -> new EmbeddedPipelineExecutor().execute(graph, config).join());
        assertTrue(failure.getCause() instanceof UnsupportedOperationException);
        assertEquals(0, sink.createdWriters.get());
    }

    @Test
    void shouldRunPeriodicCheckpointWithoutExplicitTrigger() throws Exception {
        CapturedSink sink = new CapturedSink();
        Configuration config = configuration(false);
        config.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofMillis(100));
        JobClient job = new EmbeddedPipelineExecutor().execute(
                graph(new OffsetSource(2), sink), config).get(5, TimeUnit.SECONDS);

        awaitCount(sink.rows, 4);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((!Files.exists(checkpointDirectory.resolve("checkpoint.bin"))
                || sink.checkpointFlushes.get() == 0) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(Files.exists(checkpointDirectory.resolve("checkpoint.bin")));
        assertTrue(sink.checkpointFlushes.get() > 0);
        job.cancel().get(5, TimeUnit.SECONDS);
        try (FileCheckpointStore store = new FileCheckpointStore(checkpointDirectory)) {
            assertTrue(store.loadLatest(FileCheckpointStore.graphSignature(
                    graph(new OffsetSource(2), new CapturedSink()))).isPresent());
        }
    }

    @Test
    void shouldFailRestoreWhenNoCommittedSnapshotExists() throws Exception {
        CapturedSink sink = new CapturedSink();
        Configuration restore = configuration(true);
        JobClient job = new EmbeddedPipelineExecutor().execute(
                graph(new OffsetSource(3), sink), restore).get(5, TimeUnit.SECONDS);
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, sink.createdWriters.get());
    }

    @Test
    void shouldAbortFailedSnapshotWithoutPublishingCheckpoint() throws Exception {
        CapturedSink sink = new CapturedSink();
        Configuration config = configuration(false);
        JobClient job = new EmbeddedPipelineExecutor().execute(
                graph(new OffsetSource(2, true), sink), config).get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 4);

        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> ((EmbeddedJobClient) job).checkpoint().get(5, TimeUnit.SECONDS));
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertFalse(Files.exists(checkpointDirectory.resolve("checkpoint.bin")));
    }

    private Configuration configuration(boolean restore) {
        Configuration config = new Configuration();
        config.set(CoreOptions.DEFAULT_PARALLELISM, 1);
        config.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(30));
        config.set(CheckpointingOptions.STATE_DIRECTORY, checkpointDirectory.toString());
        config.set(CheckpointingOptions.RESTORE_LATEST, restore);
        return config;
    }

    private static StreamGraph graph(OffsetSource source, CapturedSink sink) {
        SourceTransformation<String> input =
                new SourceTransformation<>("source", source, String.class, 2);
        SinkTransformation<String> output = new SinkTransformation<>(input, "sink", sink, 1);
        input.setUid("checkpoint-stable-source");
        output.setUid("checkpoint-stable-sink");
        return new StreamGraphGenerator(output, new Configuration()).generate();
    }

    private static void awaitCount(List<?> rows, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (rows.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, rows.size(), "Timed out while waiting for test records");
    }

    private record OffsetSplit(String splitId, int offset) implements SourceSplit {}

    private static final class OffsetSource implements Source<String, OffsetSplit, Integer> {

        private final int limit;
        private final boolean failSerialization;

        private OffsetSource(int limit) {
            this(limit, false);
        }

        private OffsetSource(int limit, boolean failSerialization) {
            this.limit = limit;
            this.failSerialization = failSerialization;
        }

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.CONTINUOUS_UNBOUNDED;
        }

        @Override
        public SplitEnumerator<OffsetSplit, Integer> createEnumerator(SplitEnumeratorContext<OffsetSplit> context) {
            return enumerator(context, 0);
        }

        @Override
        public SplitEnumerator<OffsetSplit, Integer> restoreEnumerator(
                SplitEnumeratorContext<OffsetSplit> context, Integer checkpointState) {
            return enumerator(context, checkpointState);
        }

        private SplitEnumerator<OffsetSplit, Integer> enumerator(
                SplitEnumeratorContext<OffsetSplit> context, int restoredMask) {
            return new SplitEnumerator<>() {
                private int assignedMask = restoredMask;

                @Override
                public void start() {}

                @Override
                public void addReader(int subtask) {}

                @Override
                public void handleSplitRequest(int subtask) {
                    int flag = 1 << subtask;
                    if ((assignedMask & flag) == 0) {
                        assignedMask |= flag;
                        context.assignSplit(new OffsetSplit("stream-" + subtask, 0), subtask);
                    }
                }

                @Override
                public void addSplitsBack(List<OffsetSplit> splits, int subtaskId) {
                    throw new AssertionError("Whole-job restart is the only supported recovery");
                }

                @Override
                public Integer snapshotState(long checkpointId) {
                    return assignedMask;
                }

                @Override
                public void close() {}
            };
        }

        @Override
        public SourceReader<String, OffsetSplit> createReader(SourceReaderContext context) {
            return new SourceReader<>() {
                private OffsetSplit current;
                private final CompletableFuture<Void> idle = new CompletableFuture<>();

                @Override
                public void start() {
                    context.sendSplitRequest();
                }

                @Override
                public InputStatus pollNext(ReaderOutput<String> output) throws Exception {
                    if (current != null && current.offset() < limit) {
                        output.collect(current.splitId() + "-" + current.offset());
                        current = new OffsetSplit(current.splitId(), current.offset() + 1);
                        return InputStatus.MORE_AVAILABLE;
                    }
                    return InputStatus.NOTHING_AVAILABLE;
                }

                @Override
                public CompletableFuture<Void> isAvailable() {
                    return idle;
                }

                @Override
                public void addSplits(List<OffsetSplit> splits) {
                    if (splits.size() != 1 || current != null) {
                        throw new IllegalArgumentException("Expected exactly one active split per Reader");
                    }
                    current = splits.getFirst();
                }

                @Override
                public void notifyNoMoreSplits() {}

                @Override
                public List<OffsetSplit> snapshotState(long checkpointId) {
                    return current == null ? List.of() : List.of(current);
                }

                @Override
                public void close() {}
            };
        }

        @Override
        public SimpleVersionedSerializer<OffsetSplit> getSplitSerializer() {
            return new SimpleVersionedSerializer<>() {
                @Override
                public int getVersion() {
                    return 1;
                }

                @Override
                public byte[] serialize(OffsetSplit split) throws IOException {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (DataOutputStream out = new DataOutputStream(bytes)) {
                        out.writeUTF(split.splitId());
                        out.writeInt(split.offset());
                    }
                    return bytes.toByteArray();
                }

                @Override
                public OffsetSplit deserialize(int version, byte[] data) throws IOException {
                    if (version != 1) {
                        throw new IOException("Unsupported split state version");
                    }
                    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
                        return new OffsetSplit(in.readUTF(), in.readInt());
                    }
                }
            };
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            return new SimpleVersionedSerializer<>() {
                @Override
                public int getVersion() {
                    return 1;
                }

                @Override
                public byte[] serialize(Integer value) throws IOException {
                    if (failSerialization) {
                        throw new IOException("checkpoint state serializer failed");
                    }
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (DataOutputStream out = new DataOutputStream(bytes)) {
                        out.writeInt(value);
                    }
                    return bytes.toByteArray();
                }

                @Override
                public Integer deserialize(int version, byte[] bytes) throws IOException {
                    if (version != 1) {
                        throw new IOException("Unsupported enumerator state version");
                    }
                    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                        return in.readInt();
                    }
                }
            };
        }
    }

    private static final class CapturedSink implements Sink<String> {

        private final List<String> rows = new CopyOnWriteArrayList<>();
        private final AtomicInteger checkpointFlushes = new AtomicInteger();
        private final AtomicInteger finalFlushes = new AtomicInteger();
        private final AtomicInteger createdWriters = new AtomicInteger();

        @Override
        public SinkWriter<String> createWriter() {
            createdWriters.incrementAndGet();
            return new SinkWriter<>() {
                @Override
                public void write(String row) {
                    rows.add(row);
                }

                @Override
                public void flush(boolean endOfInput) {
                    if (endOfInput) {
                        finalFlushes.incrementAndGet();
                    } else {
                        checkpointFlushes.incrementAndGet();
                    }
                }

                @Override
                public void close() {}
            };
        }
    }
}
