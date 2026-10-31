package io.yak.ops.flow.runtime.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.StatefulSinkWriter;
import io.yak.ops.core.api.connector.sink.SupportsWriterState;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.CancellableSinkWriter;
import io.yak.ops.core.api.connector.sink.Committer;
import io.yak.ops.core.api.connector.sink.CommittingSinkWriter;
import io.yak.ops.core.api.connector.sink.SupportsCommitter;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.api.operators.Collector;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.configuration.CoreOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.DataChangeEvent;
import io.yak.ops.core.event.Event;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.sink.DefaultDataChangeEventHashFunctionProvider;
import io.yak.ops.core.types.VarCharType;
import io.yak.ops.flow.runtime.execution.EmbeddedJobClient;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.executiongraph.ExecutionState;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.graph.StreamPartitioning;
import io.yak.ops.flow.runtime.partitioning.PartitioningEvent;
import io.yak.ops.flow.runtime.partitioning.PostPartitionProcessor;
import io.yak.ops.flow.runtime.partitioning.RegularPrePartitionOperator;
import io.yak.ops.flow.runtime.operators.OneInputStreamOperator;
import io.yak.ops.flow.runtime.operators.CheckpointedStreamOperator;
import io.yak.ops.flow.runtime.state.OperatorStateBackend;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    void eventPipelineRestoresOffsetsAndCommitsOnlyAfterDurabilityAcrossRepartitioning() throws Exception {
        CommittingEventSink initialSink = new CommittingEventSink(checkpointDirectory);
        EmbeddedJobClient first = (EmbeddedJobClient) new EmbeddedPipelineExecutor()
                .execute(eventGraph(new OffsetSource(4), initialSink), configuration(false)).get(5, TimeUnit.SECONDS);
        try {
            awaitCount(initialSink.rows, 8);
            assertEquals(0, initialSink.committedRows.get(), "Writing Event records must not commit a transaction");
            CheckpointSnapshot snapshot = first.checkpoint().get(5, TimeUnit.SECONDS);
            assertTrue(initialSink.committed.await(5, TimeUnit.SECONDS));
            assertEquals(8, initialSink.committedRows.get());
            assertEquals(2, snapshot.readerSplits().size());
            assertEquals(3, snapshot.operatorStates().keySet().stream()
                    .filter(key -> key.uid().equals("event-sink")).count());
        } finally {
            first.cancel().get(5, TimeUnit.SECONDS);
        }
        assertEquals(JobStatus.CANCELED, first.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, initialSink.finalFlushes.get());
        assertEquals(3, initialSink.closedWriters.get());
        assertEquals(3, initialSink.cancelledWriters.get());

        CommittingEventSink restoredSink = new CommittingEventSink(checkpointDirectory);
        EmbeddedJobClient restored = (EmbeddedJobClient) new EmbeddedPipelineExecutor()
                .execute(eventGraph(new OffsetSource(6), restoredSink), configuration(true)).get(5, TimeUnit.SECONDS);
        try {
            awaitCount(restoredSink.rows, 4);
            assertEquals(Set.of("stream-0-4", "stream-0-5", "stream-1-4", "stream-1-5"), Set.copyOf(restoredSink.rows));
            assertEquals(8, restoredSink.recoveredRows.get(), "Only durable committables may be replayed");
            restored.checkpoint().get(5, TimeUnit.SECONDS);
            assertTrue(restoredSink.committed.await(5, TimeUnit.SECONDS));
            assertEquals(4, restoredSink.committedRows.get());
        } finally {
            restored.cancel().get(5, TimeUnit.SECONDS);
        }
        assertEquals(0, restoredSink.finalFlushes.get());
        assertEquals(3, restoredSink.closedWriters.get());
    }

    private static StreamGraph eventGraph(OffsetSource source, Sink<Event> sink) {
        TableId table = new TableId("inventory", null, "events");
        Schema schema = new Schema(List.of(new PhysicalColumn("id", new VarCharType(64))), List.of("id"));
        SourceTransformation<String> input = new SourceTransformation<>("source", source, String.class, 2);
        OneInputTransformation<String, Event> events = new OneInputTransformation<>(input, "events", () ->
                new OneInputStreamOperator<>() {
                    private boolean announced;

                    @Override
                    public void processElement(String element, Collector<Event> output) throws Exception {
                        if (!announced) {
                            output.collect(new CreateTableEvent(table, schema));
                            announced = true;
                        }
                        output.collect(DataChangeEvent.insertEvent(table, GenericRowData.of(element)));
                    }
                }, Event.class, 2);
        OneInputTransformation<Event, PartitioningEvent> pre = new OneInputTransformation<>(
                events, "PrePartition", () -> new RegularPrePartitionOperator(3,
                        new DefaultDataChangeEventHashFunctionProvider()), PartitioningEvent.class, 2);
        OneInputTransformation<PartitioningEvent, Event> post = new OneInputTransformation<>(
                pre, "PostPartition", PostPartitionProcessor::new, Event.class, 3);
        post.setInputPartitioning(StreamPartitioning.EVENT);
        SinkTransformation<Event> output = new SinkTransformation<>(post, "sink", sink, 3);
        input.setUid("event-source");
        events.setUid("event-converter");
        pre.setUid("event-pre-partition");
        post.setUid("event-post-partition");
        output.setUid("event-sink");
        return new StreamGraphGenerator(output, new Configuration()).generate();
    }

    private static final class CommittingEventSink implements SupportsCommitter<Event, Integer> {
        private final Path directory;
        private final List<String> rows = new CopyOnWriteArrayList<>();
        private final AtomicInteger committedRows = new AtomicInteger();
        private final AtomicInteger recoveredRows = new AtomicInteger();
        private final AtomicInteger finalFlushes = new AtomicInteger();
        private final AtomicInteger closedWriters = new AtomicInteger();
        private final AtomicInteger cancelledWriters = new AtomicInteger();
        private final java.util.concurrent.CountDownLatch committed = new java.util.concurrent.CountDownLatch(3);

        private CommittingEventSink(Path directory) {
            this.directory = directory;
        }

        @Override
        public CommittingSinkWriter<Event, Integer> createWriter(WriterInitContext context) {
            return new EventWriter();
        }

        @Override
        public Committer<Integer> createCommitter(WriterInitContext context) {
            return new Committer<>() {
                @Override
                public void commit(Collection<Integer> committables, boolean recovered) {
                    assertTrue(Files.isRegularFile(directory.resolve("checkpoint.bin")),
                            "The checkpoint must be durable before a transaction commits");
                    int count = committables.stream().mapToInt(Integer::intValue).sum();
                    if (recovered) {
                        recoveredRows.addAndGet(count);
                    } else {
                        committedRows.addAndGet(count);
                        committed.countDown();
                    }
                }

                @Override
                public void recover(Collection<Integer> committables) {}

                @Override
                public void close() {}
            };
        }

        @Override
        public SimpleVersionedSerializer<Integer> getCommittableSerializer() {
            return intStateSerializer();
        }

        private final class EventWriter implements CommittingSinkWriter<Event, Integer>, CancellableSinkWriter<Event> {
            private int pending;
            private boolean schemaKnown;
            private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();

            @Override
            public void write(Event event, Context context) {
                if (event instanceof CreateTableEvent) {
                    schemaKnown = true;
                } else if (event instanceof DataChangeEvent data) {
                    assertTrue(schemaKnown, "Every sink subtask must observe schema before data, including after restore");
                    rows.add(data.after().getString(0));
                    pending++;
                } else {
                    throw new AssertionError("Unexpected pipeline event");
                }
            }

            @Override
            public Collection<Integer> prepareCommit(long checkpointId) {
                int prepared = pending;
                pending = 0;
                return List.of(prepared);
            }

            @Override
            public void flush(boolean endOfInput) {
                if (endOfInput) {
                    finalFlushes.incrementAndGet();
                }
            }

            @Override
            public void cancel() {
                if (cancelled.compareAndSet(false, true)) {
                    cancelledWriters.incrementAndGet();
                }
            }

            @Override
            public void close() {
                closedWriters.incrementAndGet();
            }
        }
    }

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

        // Restore into a fresh Source, JobID and Connector using stable UIDs and serialized state only.
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
    void shouldFreezeMutableReaderSplitStateBeforeResumingAfterBarrier() throws Exception {
        OffsetSource source = new OffsetSource(3);
        WaitingSink sink = new WaitingSink();
        JobClient job = new EmbeddedPipelineExecutor().execute(
                graph(source, sink), configuration(false)).get(5, TimeUnit.SECONDS);
        try {
            awaitCount(sink.rows, 6);
            CompletableFuture<CheckpointSnapshot> inFlight = ((EmbeddedJobClient) job).checkpoint();
            assertTrue(sink.checkpointFlushEntered.await(5, TimeUnit.SECONDS));

            // Barrier alignment reached the Sink, but its ACK has not completed. Reader
            // resumes and mutates the same Split objects it returned to snapshotState().
            source.setLimit(5);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (source.emittedRecords.get() < 10 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(10, source.emittedRecords.get(),
                    "Reader must progress while the Sink is still awaiting its barrier ACK");

            sink.releaseCheckpoint.countDown();
            CheckpointSnapshot saved = inFlight.get(5, TimeUnit.SECONDS);
            assertEquals(2, saved.readerSplits().size());
            for (var section : saved.readerSplits().values()) {
                assertEquals(1, section.size());
                var splitState = section.getFirst();
                assertEquals(3, source.getSplitSerializer()
                        .deserialize(splitState.version(), splitState.bytes()).offset());
            }
        } finally {
            sink.releaseCheckpoint.countDown();
            job.cancel().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldRestartAllSubtasksFromTheDurableCheckpointWithNewExecutionAttempts() throws Exception {
        OffsetSource source = new OffsetSource(4, false, true, false);
        CapturedSink sink = new CapturedSink();
        Configuration config = configuration(false);
        config.set(ExecutionOptions.MAX_RESTART_ATTEMPTS, 1);
        EmbeddedJobClient job = (EmbeddedJobClient) new EmbeddedPipelineExecutor()
                .execute(graph(source, sink), config).get(5, TimeUnit.SECONDS);

        awaitCount(sink.rows, 8);
        // The injected Reader fails immediately after a *completed* checkpoint and Source resume.
        // Its previous task must not be restarted in-place or replay from offset zero.
        try {
            job.checkpoint().get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException concurrentReaderFailure) {
            // Failure may race with the final checkpoint callback; durable state is the recovery authority.
        }
        awaitAttempt(job, 1);
        assertEquals(1, job.getExecutionGraph().getJobVertices().getFirst()
                .getTaskVertex(1).getCurrentExecutionAttempt().getAttemptNumber());
        assertEquals(1, job.getExecutionGraph().getJobVertices().getLast()
                .getTaskVertex(0).getCurrentExecutionAttempt().getAttemptNumber());
        // ExecutionVertex advances before the new SourceTask has finished opening its Reader.
        long restoredDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((source.createdReaders.get() < 4 || !source.restoredOffsets.contains(4))
                && System.nanoTime() < restoredDeadline) {
            Thread.sleep(10);
        }
        assertTrue(source.createdReaders.get() >= 4);
        assertTrue(source.restoredOffsets.contains(4));
        assertTrue(Files.exists(checkpointDirectory.resolve("checkpoint.bin")));

        source.setLimit(6);
        awaitCount(sink.rows, 12);
        assertEquals(Set.of(
                        "stream-0-0", "stream-0-1", "stream-0-2", "stream-0-3", "stream-0-4", "stream-0-5",
                        "stream-1-0", "stream-1-1", "stream-1-2", "stream-1-3", "stream-1-4", "stream-1-5"),
                Set.copyOf(sink.rows));
        assertEquals(12, sink.rows.size(), "Restart must use saved split offsets, not replay from zero");
        job.cancel().get(5, TimeUnit.SECONDS);
    }

    @Test
    void publishedCheckpointSurvivesFailedEnumeratorCompletionNotification() throws Exception {
        OffsetSource source = new OffsetSource(4);
        source.failNextEnumeratorCompletion();
        CapturedSink sink = new CapturedSink();
        EmbeddedJobClient job = (EmbeddedJobClient) new EmbeddedPipelineExecutor()
                .execute(graph(source, sink), configuration(false)).get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 8);

        try {
            CompletableFuture<CheckpointSnapshot> checkpoint = job.checkpoint();
            assertTrue(source.completionNotificationEntered.await(5, TimeUnit.SECONDS));
            CheckpointSnapshot published = checkpoint.get(5, TimeUnit.SECONDS);
            assertEquals(1, published.checkpointId(), "Durable publication must not await the listener");
            assertTrue(Files.exists(checkpointDirectory.resolve("checkpoint.bin")));
        } finally {
            source.releaseCompletionNotification.countDown();
        }

        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, job.getExecutionGraph().getJobVertices().getFirst()
                .getTaskVertex(0).getCurrentExecutionAttempt().getAttemptNumber());
        try (FileCheckpointStore store = new FileCheckpointStore(checkpointDirectory)) {
            assertEquals(1, store.loadLatest(FileCheckpointStore.graphSignature(graph(source, sink)))
                    .orElseThrow().checkpointId());
        }
    }

    @Test
    void restartsFromThePublishedCheckpointWhenCompletionNotificationFails() throws Exception {
        OffsetSource source = new OffsetSource(4);
        source.failNextEnumeratorCompletion();
        CapturedSink sink = new CapturedSink();
        Configuration config = configuration(false);
        config.set(ExecutionOptions.MAX_RESTART_ATTEMPTS, 1);
        EmbeddedJobClient job = (EmbeddedJobClient) new EmbeddedPipelineExecutor()
                .execute(graph(source, sink), config).get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 8);

        try {
            CompletableFuture<CheckpointSnapshot> checkpoint = job.checkpoint();
            assertTrue(source.completionNotificationEntered.await(5, TimeUnit.SECONDS));
            assertEquals(1, checkpoint.get(5, TimeUnit.SECONDS).checkpointId());
        } finally {
            source.releaseCompletionNotification.countDown();
        }

        awaitAttempt(job, 1);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!source.restoredOffsets.contains(4) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(source.restoredOffsets.contains(4), "Recovery must resume from published CP-1");
        source.setLimit(6);
        awaitCount(sink.rows, 12);
        assertEquals(12, sink.rows.size(), "Restored Source must not replay records before CP-1");
        job.cancel().get(5, TimeUnit.SECONDS);
    }

    @Test
    void sourceEndOfInputMustNotMaskFailedCheckpointEventDelivery() throws Exception {
        OffsetSource source = new OffsetSource(4);
        source.enableBoundedInput();
        source.sendEventOnNextCheckpoint();
        WaitingSink sink = new WaitingSink();
        EmbeddedJobClient job = (EmbeddedJobClient) new EmbeddedPipelineExecutor()
                .execute(graph(source, sink), configuration(false)).get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 8);

        CompletableFuture<CheckpointSnapshot> checkpoint = job.checkpoint();
        try {
            assertTrue(sink.checkpointFlushEntered.await(5, TimeUnit.SECONDS));
            source.releaseBoundedInput();
            assertTrue(source.boundedReadersFinished.await(5, TimeUnit.SECONDS));
            assertEquals(ExecutionState.RUNNING,
                    job.getExecutionGraph().getJobVertices().getFirst()
                            .getTaskVertex(0).getCurrentExecutionAttempt().getState(),
                    "Source waits for downstream EndOfData acknowledgment before physical completion");
        } finally {
            sink.releaseCheckpoint.countDown();
        }

        assertEquals(1, checkpoint.get(5, TimeUnit.SECONDS).checkpointId());
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertTrue(Files.exists(checkpointDirectory.resolve("checkpoint.bin")));
    }

    @Test
    void shouldNotRetryWhenNoCompletedCheckpointExists() throws Exception {
        OffsetSource source = new OffsetSource(3, false, false, true);
        CapturedSink sink = new CapturedSink();
        Configuration config = configuration(false);
        config.set(ExecutionOptions.MAX_RESTART_ATTEMPTS, 2);
        EmbeddedJobClient job = (EmbeddedJobClient) new EmbeddedPipelineExecutor()
                .execute(graph(source, sink), config).get(5, TimeUnit.SECONDS);

        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, job.getExecutionGraph().getJobVertices().getFirst()
                .getTaskVertex(0).getCurrentExecutionAttempt().getAttemptNumber());
        assertFalse(Files.exists(checkpointDirectory.resolve("checkpoint.bin")));
    }

    private static void awaitAttempt(EmbeddedJobClient client, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (client.getExecutionGraph().getJobVertices().getFirst()
                .getTaskVertex(0).getCurrentExecutionAttempt().getAttemptNumber() < expected
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, client.getExecutionGraph().getJobVertices().getFirst()
                .getTaskVertex(0).getCurrentExecutionAttempt().getAttemptNumber());
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
    void shouldRestoreStatefulIntermediateOperatorsAfterAlignedBarriers() throws Exception {
        CapturedSink sink = new CapturedSink();
        JobClient first = new EmbeddedPipelineExecutor().execute(
                graphWithOperator(new OffsetSource(4), sink, new AtomicInteger()), configuration(false))
                .get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 8);
        CheckpointSnapshot saved = ((EmbeddedJobClient) first).checkpoint().get(5, TimeUnit.SECONDS);
        assertEquals(2, saved.operatorStates().size());
        assertEquals(8, saved.operatorStates().values().stream()
                .mapToInt(state -> java.nio.ByteBuffer.wrap(state.get("operator/count").bytes()).getInt())
                .sum());
        first.cancel().get(5, TimeUnit.SECONDS);

        OffsetSource source = new OffsetSource(4);
        CapturedSink resumedSink = new CapturedSink();
        AtomicInteger restored = new AtomicInteger();
        JobClient second = new EmbeddedPipelineExecutor().execute(
                graphWithOperator(source, resumedSink, restored), configuration(true))
                .get(5, TimeUnit.SECONDS);
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (restored.get() != 8 && System.nanoTime() < until) {
            Thread.sleep(10);
        }
        assertEquals(8, restored.get());
        source.setLimit(6);
        awaitCount(resumedSink.rows, 4);
        second.cancel().get(5, TimeUnit.SECONDS);
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
    void shouldRejectPartialFinishedCheckpointRestoreWithoutReplayingBusinessWork() throws Exception {
        OffsetSource source = new OffsetSource(4);
        CapturedSink sink = new CapturedSink();
        String signature = FileCheckpointStore.graphSignature(graph(source, sink));
        var enumerator = new CheckpointSnapshot.SerializedState(
                source.getEnumeratorCheckpointSerializer().getVersion(),
                source.getEnumeratorCheckpointSerializer().serialize(0));
        var snapshot = new CheckpointSnapshot(
                9,
                signature,
                enumerator,
                Map.of(),
                Map.of(),
                System.currentTimeMillis(),
                Map.of(),
                Set.of("checkpoint-stable-source"),
                Set.of());
        try (FileCheckpointStore store = new FileCheckpointStore(checkpointDirectory)) {
            store.save(snapshot);
        }

        JobClient job = new EmbeddedPipelineExecutor()
                .execute(graph(source, sink), configuration(true))
                .get(5, TimeUnit.SECONDS);
        java.util.concurrent.ExecutionException failure = assertThrows(
                java.util.concurrent.ExecutionException.class,
                () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause().toString().contains("completely finished terminal checkpoint"));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, source.createdReaders.get());
        assertEquals(0, sink.createdWriters.get());
    }

    @Test
    void shouldRequireFinalCheckpointAfterAllBoundedRecordsAndSinkFlush() throws Exception {
        OffsetSource source = new OffsetSource(3);
        source.enableBoundedInput();
        CapturedSink sink = new CapturedSink();
        Configuration config = configuration(false);
        EmbeddedJobClient job = (EmbeddedJobClient) new EmbeddedPipelineExecutor()
                .execute(graph(source, sink), config).get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 6);
        source.releaseBoundedInput();
        job.getJobExecutionResult().get(10, TimeUnit.SECONDS);

        assertEquals(JobStatus.FINISHED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(1, sink.finalFlushes.get());
        assertEquals(6, sink.rows.size());
        try (FileCheckpointStore store = new FileCheckpointStore(checkpointDirectory)) {
            CheckpointSnapshot snapshot = store.loadLatest(FileCheckpointStore.graphSignature(graph(source, sink)))
                    .orElseThrow();
            assertTrue(snapshot.hasFinishedOperators());
            assertEquals(Set.of("checkpoint-stable-source", "checkpoint-stable-sink"),
                    snapshot.fullyFinishedOperators());
            assertTrue(snapshot.finishedSubtasks().isEmpty());
            assertTrue(snapshot.operatorStates().isEmpty());
        }
    }

    @Test
    void finishedOnRestoreMustNotCreateReadersOrWritersOrRepeatFinalFlush() throws Exception {
        OffsetSource source = new OffsetSource(2);
        source.enableBoundedInput();
        CapturedSink sink = new CapturedSink();
        Configuration config = configuration(false);
        JobClient first = new EmbeddedPipelineExecutor()
                .execute(graph(source, sink), config).get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 4);
        source.releaseBoundedInput();
        first.getJobExecutionResult().get(10, TimeUnit.SECONDS);
        assertEquals(1, sink.finalFlushes.get());

        OffsetSource restoredSource = new OffsetSource(8);
        restoredSource.enableBoundedInput();
        CapturedSink restoredSink = new CapturedSink();
        Configuration restore = configuration(true);
        JobClient second = new EmbeddedPipelineExecutor()
                .execute(graph(restoredSource, restoredSink), restore).get(5, TimeUnit.SECONDS);
        second.getJobExecutionResult().get(10, TimeUnit.SECONDS);

        assertEquals(JobStatus.FINISHED, second.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, restoredSource.createdReaders.get());
        assertEquals(0, restoredSink.createdWriters.get());
        assertEquals(0, restoredSink.finalFlushes.get());
        assertTrue(restoredSink.rows.isEmpty());
        assertEquals(4, sink.rows.size());
    }

    @Test
    void finishedOnRestoreMustSkipIntermediateOperatorFactories() throws Exception {
        OffsetSource source = new OffsetSource(2);
        source.enableBoundedInput();
        CapturedSink sink = new CapturedSink();
        AtomicInteger initialOperators = new AtomicInteger();
        JobClient original = new EmbeddedPipelineExecutor()
                .execute(graphWithCountingOperator(source, sink, initialOperators), configuration(false))
                .get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 4);
        source.releaseBoundedInput();
        original.getJobExecutionResult().get(10, TimeUnit.SECONDS);
        assertEquals(2, initialOperators.get());

        OffsetSource restoredSource = new OffsetSource(100);
        restoredSource.enableBoundedInput();
        CapturedSink restoredSink = new CapturedSink();
        AtomicInteger restoredOperators = new AtomicInteger();
        JobClient restored = new EmbeddedPipelineExecutor()
                .execute(graphWithCountingOperator(restoredSource, restoredSink, restoredOperators), configuration(true))
                .get(5, TimeUnit.SECONDS);
        restored.getJobExecutionResult().get(10, TimeUnit.SECONDS);
        assertEquals(JobStatus.FINISHED, restored.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(0, restoredSource.createdReaders.get());
        assertEquals(0, restoredOperators.get());
        assertEquals(0, restoredSink.createdWriters.get());
        assertTrue(restoredSink.rows.isEmpty());
    }

    @Test
    void publishedFinalCheckpointRecoversWithoutReplayingAmbiguousSinkOutput() throws Exception {
        OffsetSource source = new OffsetSource(2);
        source.enableBoundedInput();
        source.failNextEnumeratorCompletion();
        CapturedSink sink = new CapturedSink();
        Configuration config = configuration(false);
        config.set(ExecutionOptions.MAX_RESTART_ATTEMPTS, 1);
        JobClient job = new EmbeddedPipelineExecutor()
                .execute(graph(source, sink), config).get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 4);
        source.releaseBoundedInput();

        try {
            assertTrue(source.completionNotificationEntered.await(10, TimeUnit.SECONDS));
            assertEquals(1, sink.finalFlushes.get());
        } finally {
            source.releaseCompletionNotification.countDown();
        }
        job.getJobExecutionResult().get(10, TimeUnit.SECONDS);
        assertEquals(JobStatus.FINISHED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(2, source.createdReaders.get(), "Finished recovery cannot create additional SourceReaders");
        assertEquals(1, sink.createdWriters.get(), "Finished recovery cannot recreate the SinkWriter");
        assertEquals(1, sink.finalFlushes.get());
        assertEquals(4, sink.rows.size());
    }

    @Test
    void transientFinalCheckpointDeclineRetriesOnceWithoutRepeatingSinkFinish() throws Exception {
        OffsetSource source = new OffsetSource(2);
        source.enableBoundedInput();
        source.declineNextEnumeratorSnapshot();
        CapturedSink sink = new CapturedSink();
        JobClient job = new EmbeddedPipelineExecutor()
                .execute(graph(source, sink), configuration(false)).get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 4);
        source.releaseBoundedInput();
        job.getJobExecutionResult().get(10, TimeUnit.SECONDS);

        assertEquals(JobStatus.FINISHED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(2, source.enumeratorSnapshots.get(), "One safe in-attempt retry is allowed");
        assertEquals(1, sink.finalFlushes.get(), "A checkpoint retry must not repeat final Sink flush");
        try (FileCheckpointStore store = new FileCheckpointStore(checkpointDirectory)) {
            assertEquals(2,
                    store.loadLatest(FileCheckpointStore.graphSignature(graph(source, sink)))
                            .orElseThrow().checkpointId());
        }
    }

    @Test
    void failedRequiredFinalCheckpointMustNotFinishTheJobOrOverwriteAnOlderSnapshot() throws Exception {
        OffsetSource source = new OffsetSource(2, true);
        source.enableBoundedInput();
        CapturedSink sink = new CapturedSink();
        Configuration config = configuration(false);
        JobClient job = new EmbeddedPipelineExecutor()
                .execute(graph(source, sink), config).get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 4);
        source.releaseBoundedInput();
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> job.getJobExecutionResult().get(10, TimeUnit.SECONDS));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(1, sink.finalFlushes.get());
        assertFalse(Files.exists(checkpointDirectory.resolve("checkpoint.bin")));
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

    @Test
    void failedSinkCheckpointFlushMustFailTheJobWithoutPublishingDurableState() throws Exception {
        CapturedSink sink = new CapturedSink(true);
        JobClient job = new EmbeddedPipelineExecutor().execute(
                graph(new OffsetSource(2), sink), configuration(false)).get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 4);

        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> ((EmbeddedJobClient) job).checkpoint().get(5, TimeUnit.SECONDS));
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> job.getJobExecutionResult().get(5, TimeUnit.SECONDS));
        assertEquals(JobStatus.FAILED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(1, sink.checkpointFlushes.get());
        assertEquals(0, sink.finalFlushes.get(), "A failed checkpoint cannot be treated as a normal finish");
        assertEquals(1, sink.closedWriters.get());
        assertFalse(Files.exists(checkpointDirectory.resolve("checkpoint.bin")));
    }

    @Test
    void shouldRestoreStatefulSinkWriterFromDurableAlignedCheckpoint() throws Exception {
        DurableWriterSink sink = new DurableWriterSink();
        JobClient first = new EmbeddedPipelineExecutor().execute(
                graph(new OffsetSource(4), sink), configuration(false)).get(5, TimeUnit.SECONDS);
        awaitCount(sink.rows, 8);
        CheckpointSnapshot saved = ((EmbeddedJobClient) first).checkpoint().get(5, TimeUnit.SECONDS);
        var key = new CheckpointSnapshot.OperatorSubtask("checkpoint-stable-sink", 0);
        assertTrue(saved.operatorStates().containsKey(key));
        assertEquals(8, java.nio.ByteBuffer.wrap(
                saved.operatorStates().get(key).get("operator/writer-0").bytes()).getInt());
        first.cancel().get(5, TimeUnit.SECONDS);

        OffsetSource source = new OffsetSource(4);
        DurableWriterSink restored = new DurableWriterSink();
        JobClient second = new EmbeddedPipelineExecutor().execute(
                graph(source, restored), configuration(true)).get(5, TimeUnit.SECONDS);
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (restored.restoredCount.get() != 8 && System.nanoTime() < until) {
            Thread.sleep(10);
        }
        assertEquals(8, restored.restoredCount.get());
        source.setLimit(5);
        awaitCount(restored.rows, 2);
        second.cancel().get(5, TimeUnit.SECONDS);
    }

    private Configuration configuration(boolean restore) {
        Configuration config = new Configuration();
        config.set(CoreOptions.DEFAULT_PARALLELISM, 1);
        config.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(30));
        config.set(CheckpointingOptions.STATE_DIRECTORY, checkpointDirectory.toString());
        config.set(CheckpointingOptions.RESTORE_LATEST, restore);
        return config;
    }

    private static StreamGraph graph(OffsetSource source, Sink<String> sink) {
        SourceTransformation<String> input =
                new SourceTransformation<>("source", source, String.class, 2);
        SinkTransformation<String> output = new SinkTransformation<>(input, "sink", sink, 1);
        input.setUid("checkpoint-stable-source");
        output.setUid("checkpoint-stable-sink");
        return new StreamGraphGenerator(output, new Configuration()).generate();
    }

    private static StreamGraph graphWithCountingOperator(
            OffsetSource source, CapturedSink sink, AtomicInteger factoryCalls) {
        SourceTransformation<String> input =
                new SourceTransformation<>("source", source, String.class, 2);
        OneInputTransformation<String, String> mapper = new OneInputTransformation<>(
                input, "mapper", () -> {
                    factoryCalls.incrementAndGet();
                    return new OneInputStreamOperator<>() {
                        @Override
                        public void processElement(String value, Collector<String> output) throws Exception {
                            output.collect(value);
                        }
                    };
                }, String.class, 2);
        SinkTransformation<String> target = new SinkTransformation<>(mapper, "sink", sink, 1);
        input.setUid("checkpoint-stable-source");
        mapper.setUid("checkpoint-stable-mapper");
        target.setUid("checkpoint-stable-sink");
        return new StreamGraphGenerator(target, new Configuration()).generate();
    }

    private static StreamGraph graphWithOperator(
            OffsetSource source, CapturedSink sink, AtomicInteger restored) {
        SourceTransformation<String> input =
                new SourceTransformation<>("source", source, String.class, 2);
        OneInputTransformation<String, String> counter = new OneInputTransformation<>(
                input, "counter", () -> new DurableCounter(restored), String.class, 2);
        SinkTransformation<String> output = new SinkTransformation<>(counter, "sink", sink, 1);
        input.setUid("checkpoint-stable-source");
        counter.setUid("checkpoint-stable-counter");
        output.setUid("checkpoint-stable-sink");
        return new StreamGraphGenerator(output, new Configuration()).generate();
    }

    private static final class DurableCounter
            implements OneInputStreamOperator<String, String>, CheckpointedStreamOperator {
        private final AtomicInteger restored;
        private OperatorStateBackend backend;
        private int count;

        private DurableCounter(AtomicInteger restored) {
            this.restored = restored;
        }

        @Override
        public void initializeState(OperatorStateBackend backend) throws Exception {
            this.backend = backend;
            this.count = backend.get("count", intStateSerializer()).orElse(0);
            restored.addAndGet(count);
        }

        @Override
        public void processElement(String element, Collector<String> output) throws Exception {
            count++;
            output.collect(element);
        }

        @Override
        public void snapshotState(long checkpointId, OperatorStateBackend state) throws Exception {
            state.put("count", count, intStateSerializer());
        }
    }

    private static SimpleVersionedSerializer<Integer> intStateSerializer() {
        return new SimpleVersionedSerializer<>() {
            @Override
            public int getVersion() { return 1; }

            @Override
            public byte[] serialize(Integer value) {
                return java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
            }

            @Override
            public Integer deserialize(int version, byte[] bytes) throws IOException {
                if (version != 1 || bytes.length != Integer.BYTES) {
                    throw new IOException("Invalid count state");
                }
                return java.nio.ByteBuffer.wrap(bytes).getInt();
            }
        };
    }

    private static void awaitCount(List<?> rows, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (rows.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, rows.size(), "Timed out while waiting for test records");
    }

    /** Mutable on purpose: a reader may update the same Split object after it resumes. */
    private static final class OffsetSplit implements SourceSplit {
        private final String splitId;
        private int offset;

        private OffsetSplit(String splitId, int offset) {
            this.splitId = splitId;
            this.offset = offset;
        }

        @Override
        public String splitId() {
            return splitId;
        }

        private int offset() {
            return offset;
        }

        private void advance() {
            offset++;
        }
    }

    private record CheckpointSourceEvent() implements SourceEvent {}

    private static final class OffsetSource implements Source<String, OffsetSplit, Integer> {

        private volatile int limit;
        private volatile boolean bounded;
        private volatile boolean finishAllowed;
        private final java.util.concurrent.CountDownLatch boundedReadersFinished =
                new java.util.concurrent.CountDownLatch(2);
        private final boolean failSerialization;
        private final boolean failAfterCheckpoint;
        private final boolean failBeforeCheckpoint;
        private final AtomicInteger createdReaders = new AtomicInteger();
        private final AtomicInteger emittedRecords = new AtomicInteger();
        private final AtomicInteger enumeratorSnapshots = new AtomicInteger();
        private final java.util.concurrent.atomic.AtomicBoolean declineEnumeratorSnapshot =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final List<Integer> restoredOffsets = new CopyOnWriteArrayList<>();
        private final List<CompletableFuture<Void>> availabilityWaiters = new CopyOnWriteArrayList<>();
        private final java.util.concurrent.atomic.AtomicBoolean checkpointNotified =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean faultTriggered =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean failEnumeratorCompletion =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean sendEventOnCompletion =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean failSourceEventDelivery =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.CountDownLatch completionNotificationEntered =
                new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch releaseCompletionNotification =
                new java.util.concurrent.CountDownLatch(1);

        private OffsetSource(int limit) {
            this(limit, false);
        }

        private OffsetSource(int limit, boolean failSerialization) {
            this(limit, failSerialization, false, false);
        }

        private OffsetSource(int limit, boolean failSerialization,
                boolean failAfterCheckpoint, boolean failBeforeCheckpoint) {
            this.limit = limit;
            this.failSerialization = failSerialization;
            this.failAfterCheckpoint = failAfterCheckpoint;
            this.failBeforeCheckpoint = failBeforeCheckpoint;
        }

        private void setLimit(int limit) {
            this.limit = limit;
            for (CompletableFuture<Void> waiter : availabilityWaiters) {
                waiter.complete(null);
            }
            availabilityWaiters.clear();
        }

        private void failNextEnumeratorCompletion() {
            failEnumeratorCompletion.set(true);
        }

        private void declineNextEnumeratorSnapshot() {
            declineEnumeratorSnapshot.set(true);
        }

        private void sendEventOnNextCheckpoint() {
            sendEventOnCompletion.set(true);
            failSourceEventDelivery.set(true);
        }

        private void enableBoundedInput() {
            bounded = true;
        }

        private void releaseBoundedInput() {
            finishAllowed = true;
            setLimit(limit);
        }

        @Override
        public Boundedness getBoundedness() {
            if (bounded) {
                return Boundedness.BOUNDED;
            }
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
                public void handleSplitRequest(int subtask, String requesterHostname) {
                    int flag = 1 << subtask;
                    if ((assignedMask & flag) == 0) {
                        assignedMask |= flag;
                        context.assignSplit(new OffsetSplit("stream-" + subtask, 0), subtask);
                    }
                    if (bounded) {
                        context.signalNoMoreSplits(subtask);
                    }
                }

                @Override
                public void addSplitsBack(List<OffsetSplit> splits, int subtaskId) {
                    throw new AssertionError("Whole-job restart is the only supported recovery");
                }

                @Override
                public Integer snapshotState(long checkpointId) throws Exception {
                    enumeratorSnapshots.incrementAndGet();
                    if (declineEnumeratorSnapshot.compareAndSet(true, false)) {
                        throw new CheckpointDeclinedException(checkpointId, "transient Enumerator snapshot decline");
                    }
                    return assignedMask;
                }

                @Override
                public void notifyCheckpointComplete(long checkpointId) throws Exception {
                    if (failEnumeratorCompletion.compareAndSet(true, false)) {
                        completionNotificationEntered.countDown();
                        if (!releaseCompletionNotification.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out awaiting Enumerator notification test");
                        }
                        throw new IllegalStateException("Enumerator notification failed after durable checkpoint");
                    }
                    if (sendEventOnCompletion.compareAndSet(true, false)) {
                        context.sendEventToSourceReader(0, new CheckpointSourceEvent());
                    }
                }

                @Override
                public void close() {}
            };
        }

        @Override
        public SourceReader<String, OffsetSplit> createReader(SourceReaderContext context) {
            createdReaders.incrementAndGet();
            return new SourceReader<>() {
                private OffsetSplit current;
                private CompletableFuture<Void> idle = new CompletableFuture<>();
                private boolean noMoreSplits;

                @Override
                public void start() {
                    context.sendSplitRequest();
                }

                @Override
                public InputStatus pollNext(ReaderOutput<String> output) throws Exception {
                    if (failBeforeCheckpoint && faultTriggered.compareAndSet(false, true)) {
                        throw new IllegalStateException("reader failed before durable checkpoint");
                    }
                    if (failAfterCheckpoint && checkpointNotified.get()
                            && faultTriggered.compareAndSet(false, true)) {
                        throw new IllegalStateException("reader failed after durable checkpoint");
                    }
                    if (current != null && current.offset() < limit) {
                        output.collect(current.splitId() + "-" + current.offset());
                        current.advance();
                        emittedRecords.incrementAndGet();
                        return InputStatus.MORE_AVAILABLE;
                    }
                    if (bounded && noMoreSplits && finishAllowed) {
                        boundedReadersFinished.countDown();
                        return InputStatus.END_OF_INPUT;
                    }
                    if (idle.isDone()) {
                        idle = new CompletableFuture<>();
                    }
                    return InputStatus.NOTHING_AVAILABLE;
                }

                @Override
                public CompletableFuture<Void> isAvailable() {
                    availabilityWaiters.add(idle);
                    return idle;
                }

                @Override
                public void addSplits(List<OffsetSplit> splits) {
                    if (splits.size() != 1 || current != null) {
                        throw new IllegalArgumentException("Expected exactly one active split per Reader");
                    }
                    current = splits.getFirst();
                    restoredOffsets.add(current.offset());
                }

                @Override
                public void notifyNoMoreSplits() {
                    noMoreSplits = true;
                    idle.complete(null);
                }

                @Override
                public void handleSourceEvents(SourceEvent event) {
                    if (event instanceof CheckpointSourceEvent && failSourceEventDelivery.get()) {
                        throw new IllegalStateException("Injected SourceEvent delivery failure after EndOfData");
                    }
                }

                @Override
                public List<OffsetSplit> snapshotState(long checkpointId) {
                    return current == null ? List.of() : List.of(current);
                }

                @Override
                public void notifyCheckpointComplete(long checkpointId) {
                    checkpointNotified.set(true);
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

    /** Holds Sink barrier ACK so the mutable Source Split can advance during alignment. */
    private static final class WaitingSink implements Sink<String> {
        private final List<String> rows = new CopyOnWriteArrayList<>();
        private final java.util.concurrent.CountDownLatch checkpointFlushEntered =
                new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch releaseCheckpoint =
                new java.util.concurrent.CountDownLatch(1);

        @Override
        public SinkWriter<String> createWriter(WriterInitContext context) {
            return new SinkWriter<>() {
                @Override
                public void write(String row, Context context) {
                    rows.add(row);
                }

                @Override
                public void flush(boolean endOfInput) throws Exception {
                    if (!endOfInput) {
                        checkpointFlushEntered.countDown();
                        if (!releaseCheckpoint.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out awaiting checkpoint test latch");
                        }
                    }
                }

                @Override
                public void close() {}
            };
        }
    }

    private static final class DurableWriterSink
            implements Sink<String>, SupportsWriterState<String, Integer> {
        private final List<String> rows = new CopyOnWriteArrayList<>();
        private final AtomicInteger restoredCount = new AtomicInteger(-1);

        @Override
        public StatefulSinkWriter<String, Integer> createWriter(WriterInitContext context) {
            throw new AssertionError("Stateful writer must use restoreWriter even without saved state");
        }

        @Override
        public StatefulSinkWriter<String, Integer> restoreWriter(
                WriterInitContext context, Collection<Integer> state) {
            int start = state.stream().mapToInt(Integer::intValue).sum();
            restoredCount.set(start);
            return new StatefulSinkWriter<>() {
                private int written = start;

                @Override
                public void write(String value, Context context) {
                    rows.add(value);
                    written++;
                }

                @Override
                public void flush(boolean endOfInput) {}

                @Override
                public List<Integer> snapshotState(long checkpointId) {
                    return List.of(written);
                }

                @Override
                public void close() {}
            };
        }

        @Override
        public SimpleVersionedSerializer<Integer> getWriterStateSerializer() {
            return intStateSerializer();
        }
    }

    private static final class CapturedSink implements Sink<String> {

        private final boolean failCheckpointFlush;
        private final List<String> rows = new CopyOnWriteArrayList<>();
        private final AtomicInteger checkpointFlushes = new AtomicInteger();
        private final AtomicInteger finalFlushes = new AtomicInteger();
        private final AtomicInteger createdWriters = new AtomicInteger();
        private final AtomicInteger closedWriters = new AtomicInteger();

        private CapturedSink() {
            this(false);
        }

        private CapturedSink(boolean failCheckpointFlush) {
            this.failCheckpointFlush = failCheckpointFlush;
        }

        @Override
        public SinkWriter<String> createWriter(WriterInitContext context) {
            createdWriters.incrementAndGet();
            return new SinkWriter<>() {
                @Override
                public void write(String row, Context context) {
                    rows.add(row);
                }

                @Override
                public void flush(boolean endOfInput) throws IOException {
                    if (endOfInput) {
                        finalFlushes.incrementAndGet();
                    } else {
                        checkpointFlushes.incrementAndGet();
                        if (failCheckpointFlush) {
                            throw new IOException("sink checkpoint flush failed");
                        }
                    }
                }

                @Override
                public void close() {
                    closedWriters.incrementAndGet();
                }
            };
        }
    }
}
