package io.yak.ops.cdc.runtime.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.runtime.api.serialization.SimpleVersionedSerializer;
import io.yak.ops.cdc.runtime.api.sink.Sink;
import io.yak.ops.cdc.runtime.api.sink.SinkWriter;
import io.yak.ops.cdc.runtime.api.sink.WriterInitContext;
import io.yak.ops.cdc.runtime.api.source.Boundedness;
import io.yak.ops.cdc.runtime.api.source.InputStatus;
import io.yak.ops.cdc.runtime.api.source.ReaderOutput;
import io.yak.ops.cdc.runtime.api.source.Source;
import io.yak.ops.cdc.runtime.api.source.SourceReader;
import io.yak.ops.cdc.runtime.api.source.SourceReaderContext;
import io.yak.ops.cdc.runtime.api.source.SourceSplit;
import io.yak.ops.cdc.runtime.api.source.SplitEnumerator;
import io.yak.ops.cdc.runtime.api.source.SplitEnumeratorContext;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.flink.cdc.common.event.Event;
import org.junit.jupiter.api.Test;

class StandalonePipelineTest {

    @Test
    void boundedSourceRunsThroughTransformAndFlushesSink() throws Exception {
        TestSource source = new TestSource(5, false);
        TestSink sink = new TestSink(false);
        EventTransformer evenOnly = (event, output) -> {
            TestEvent testEvent = (TestEvent) event;
            if (testEvent.value % 2 == 0) {
                output.collect(event);
            }
        };

        try (StandalonePipeline<TestSplit, Integer> pipeline =
                new StandalonePipeline<>(source, sink, evenOnly, 1)) {
            assertEquals(
                    StandalonePipeline.State.FINISHED,
                    pipeline.start().get(5, TimeUnit.SECONDS));
            assertEquals(List.of(2, 4), sink.values);
            assertTrue(sink.flushedAtEnd.get());
            assertTrue(sink.closed.get());
            assertTrue(source.readerClosed.get());
            assertTrue(source.enumeratorClosed.get());
            assertEquals(StandalonePipeline.State.FINISHED, pipeline.getState());
        }
    }

    @Test
    void asynchronouslyDiscoveredSplitsAreAssignedOnTheSourceThread() throws Exception {
        TestSource source = new TestSource(2, false, true);
        TestSink sink = new TestSink(false);
        try (StandalonePipeline<TestSplit, Integer> pipeline =
                new StandalonePipeline<>(source, sink, EventTransformer.identity(), 2)) {
            assertEquals(StandalonePipeline.State.FINISHED, pipeline.start().get(5, TimeUnit.SECONDS));
            assertEquals(List.of(1, 2), sink.values);
            assertTrue(source.asyncAssignmentOnSourceThread.get());
        }
    }

    @Test
    void failingSinkFailsPipelineAndClosesReader() throws Exception {
        TestSource source = new TestSource(3, false);
        TestSink sink = new TestSink(true);
        try (StandalonePipeline<TestSplit, Integer> pipeline =
                new StandalonePipeline<>(source, sink, EventTransformer.identity(), 1)) {
            CompletableFuture<StandalonePipeline.State> result = pipeline.start();
            assertThrows(CompletionException.class, () -> result.orTimeout(5, TimeUnit.SECONDS).join());
            assertEquals(StandalonePipeline.State.FAILED, pipeline.getState());
            assertNotNull(pipeline.getFailure());
            assertTrue(sink.closed.get());
            assertTrue(source.readerClosed.get());
            assertTrue(source.enumeratorClosed.get());
        }
    }

    @Test
    void unboundedReaderCanBeCancelledWhileWaitingForData() throws Exception {
        TestSource source = new TestSource(0, true);
        TestSink sink = new TestSink(false);
        try (StandalonePipeline<TestSplit, Integer> pipeline =
                new StandalonePipeline<>(source, sink, EventTransformer.identity(), 2)) {
            CompletableFuture<StandalonePipeline.State> completion = pipeline.start();
            assertTrue(source.readerStartedFuture.get(5, TimeUnit.SECONDS));
            pipeline.cancel();
            assertEquals(StandalonePipeline.State.CANCELLED, completion.get(5, TimeUnit.SECONDS));
            assertTrue(source.readerClosed.get());
            assertTrue(sink.closed.get());
            assertTrue(!sink.flushedAtEnd.get());
        }
    }

    private static final class TestEvent implements Event {
        private final int value;

        private TestEvent(int value) {
            this.value = value;
        }
    }

    private static final class TestSplit implements SourceSplit {
        @Override
        public String splitId() {
            return "only-split";
        }
    }

    private static final class TestSource implements Source<Event, TestSplit, Integer> {
        private final int count;
        private final boolean unbounded;
        private final boolean asyncAssignment;
        private final AtomicBoolean asyncAssignmentOnSourceThread = new AtomicBoolean();
        private final AtomicBoolean readerClosed = new AtomicBoolean();
        private final AtomicBoolean enumeratorClosed = new AtomicBoolean();
        private final CompletableFuture<Boolean> readerStartedFuture = new CompletableFuture<>();

        private TestSource(int count, boolean unbounded) {
            this(count, unbounded, false);
        }

        private TestSource(int count, boolean unbounded, boolean asyncAssignment) {
            this.count = count;
            this.unbounded = unbounded;
            this.asyncAssignment = asyncAssignment;
        }

        @Override
        public Boundedness getBoundedness() {
            return unbounded ? Boundedness.CONTINUOUS_UNBOUNDED : Boundedness.BOUNDED;
        }

        @Override
        public SourceReader<Event, TestSplit> createReader(SourceReaderContext context) {
            return new TestReader(context);
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> createEnumerator(
                SplitEnumeratorContext<TestSplit> context) {
            return new TestEnumerator(context);
        }

        @Override
        public SplitEnumerator<TestSplit, Integer> restoreEnumerator(
                SplitEnumeratorContext<TestSplit> context, Integer checkpoint) {
            return createEnumerator(context);
        }

        @Override
        public SimpleVersionedSerializer<TestSplit> getSplitSerializer() {
            return null;
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorCheckpointSerializer() {
            return new SimpleVersionedSerializer<Integer>() {
                @Override public int getVersion() { return 1; }
                @Override public byte[] serialize(Integer value) { return ByteBuffer.allocate(4).putInt(value).array(); }
                @Override public Integer deserialize(int version, byte[] bytes) { return ByteBuffer.wrap(bytes).getInt(); }
            };
        }

        private final class TestEnumerator implements SplitEnumerator<TestSplit, Integer> {
            private final SplitEnumeratorContext<TestSplit> context;
            private boolean assigned;

            private TestEnumerator(SplitEnumeratorContext<TestSplit> context) {
                this.context = context;
            }

            @Override
            public void start() {}

            @Override
            public void handleSplitRequest(int subtaskId, String hostname) {
                if (!asyncAssignment) {
                    assign(subtaskId);
                }
            }

            @Override
            public void addReader(int subtaskId) {
                if (asyncAssignment) {
                    context.callAsync(() -> subtaskId, (value, error) -> {
                        if (error != null) {
                            throw new IllegalStateException("Async assignment failed", error);
                        }
                        asyncAssignmentOnSourceThread.set(
                                Thread.currentThread().getName().equals("yak-cdc-source"));
                        assign(value);
                    });
                } else {
                    assign(subtaskId);
                }
            }

            private void assign(int subtaskId) {
                if (!assigned) {
                    assigned = true;
                    context.assignSplit(new TestSplit(), subtaskId);
                    if (!unbounded) {
                        context.signalNoMoreSplits(subtaskId);
                    }
                }
            }

            @Override
            public void addSplitsBack(List<TestSplit> splits, int subtaskId) {}

            @Override
            public Integer snapshotState(long checkpointId) {
                return assigned ? 1 : 0;
            }

            @Override
            public void close() throws IOException {
                enumeratorClosed.set(true);
            }
        }

        private final class TestReader implements SourceReader<Event, TestSplit> {
            private final SourceReaderContext context;
            private final AtomicInteger emitted = new AtomicInteger();
            private boolean assigned;
            private boolean noMoreSplits;

            private TestReader(SourceReaderContext context) {
                this.context = context;
            }

            @Override
            public void start() {
                readerStartedFuture.complete(true);
                context.sendSplitRequest();
            }

            @Override
            public InputStatus pollNext(ReaderOutput<Event> output) {
                if (!assigned) {
                    return InputStatus.NOTHING_AVAILABLE;
                }
                if (emitted.get() < count) {
                    output.collect(new TestEvent(emitted.incrementAndGet()));
                    return InputStatus.MORE_AVAILABLE;
                }
                return noMoreSplits ? InputStatus.END_OF_INPUT : InputStatus.NOTHING_AVAILABLE;
            }

            @Override
            public CompletableFuture<Void> isAvailable() {
                if (unbounded && emitted.get() >= count) {
                    return new CompletableFuture<>();
                }
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public List<TestSplit> snapshotState(long checkpointId) {
                return List.of(new TestSplit());
            }

            @Override
            public void addSplits(List<TestSplit> splits) {
                assigned = !splits.isEmpty();
            }

            @Override
            public void notifyNoMoreSplits() {
                noMoreSplits = true;
            }

            @Override
            public void close() throws Exception {
                readerClosed.set(true);
            }
        }
    }

    private static final class TestSink implements Sink<Event> {
        private final boolean fail;
        private final List<Integer> values = new ArrayList<>();
        private final AtomicBoolean flushedAtEnd = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private TestSink(boolean fail) {
            this.fail = fail;
        }

        @Override
        public SinkWriter<Event> createWriter(WriterInitContext context) {
            assertEquals(0, context.getSubtaskId());
            return new SinkWriter<Event>() {
                @Override
                public void write(Event event, Context recordContext) throws IOException {
                    if (fail) {
                        throw new IOException("sink failure");
                    }
                    values.add(((TestEvent) event).value);
                }

                @Override
                public void flush(boolean endOfInput) {
                    flushedAtEnd.set(endOfInput);
                }

                @Override
                public void close() {
                    closed.set(true);
                }
            };
        }
    }
}
