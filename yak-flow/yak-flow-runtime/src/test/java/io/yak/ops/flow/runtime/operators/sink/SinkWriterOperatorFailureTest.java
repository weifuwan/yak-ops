package io.yak.ops.flow.runtime.operators.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.StatefulSinkWriter;
import io.yak.ops.core.api.connector.sink.SupportsWriterState;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.state.OperatorStateBackend;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SinkWriterOperatorFailureTest {

    @Test
    void failedCheckpointFlushMustNotSnapshotWriterState() throws Exception {
        ProbeSink sink = new ProbeSink(1);
        sink.failCheckpointFlush = true;
        OperatorStateBackend state = newState(Map.of());
        SinkWriterOperator<String> operator = open(sink, state);

        try {
            operator.processElement("a", ignored -> {});
            IOException failure = assertThrows(IOException.class, () -> operator.snapshotState(10, state));
            assertEquals("checkpoint flush failed", failure.getMessage());
            assertEquals(1, sink.writer.checkpointFlushes);
            assertEquals(0, sink.writer.snapshots);
            assertTrue(state.snapshot().isEmpty(), "Failed flush must not publish writer state");
        } finally {
            operator.close();
            operator.close();
        }
        assertEquals(1, sink.writer.closes);
        assertEquals(0, sink.writer.finalFlushes);
    }

    @Test
    void failedWriterSnapshotMustNotPublishPartialState() throws Exception {
        ProbeSink sink = new ProbeSink(1);
        sink.failSnapshot = true;
        OperatorStateBackend state = newState(Map.of());
        SinkWriterOperator<String> operator = open(sink, state);
        try {
            operator.processElement("a", ignored -> {});
            IOException failure = assertThrows(IOException.class, () -> operator.snapshotState(11, state));
            assertEquals("writer snapshot failed", failure.getMessage());
            assertEquals(1, sink.writer.checkpointFlushes);
            assertEquals(1, sink.writer.snapshots);
            assertTrue(state.snapshot().isEmpty());
        } finally {
            operator.close();
        }
        assertEquals(1, sink.writer.closes);
        assertEquals(0, sink.writer.finalFlushes);
    }

    @Test
    void restoredWriterContinuesFromTheDurableStateRatherThanUncheckpointedProgress() throws Exception {
        ProbeSink firstSink = new ProbeSink(1);
        OperatorStateBackend original = newState(Map.of());
        SinkWriterOperator<String> first = open(firstSink, original);
        Map<String, io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot.SerializedState> checkpoint;
        try {
            first.processElement("a", ignored -> {});
            first.processElement("b", ignored -> {});
            first.snapshotState(7, original);
            checkpoint = original.snapshot();

            // A later write was never checkpointed and must not appear in restored writer state.
            first.processElement("uncommitted", ignored -> {});
            assertEquals(3, firstSink.writer.count);
            assertEquals(2, (int) original.get("writer-0", firstSink.getWriterStateSerializer()).orElseThrow());
        } finally {
            first.close();
        }

        ProbeSink restoredSink = new ProbeSink(1);
        OperatorStateBackend restoredState = newState(checkpoint);
        SinkWriterOperator<String> restored = open(restoredSink, restoredState);
        try {
            assertEquals(2, restoredSink.writer.count);
            assertEquals(2, restoredSink.lastRestoredCount);
            restored.processElement("c", ignored -> {});
            restored.snapshotState(8, restoredState);
            assertEquals(3, (int) restoredState.get("writer-0", restoredSink.getWriterStateSerializer()).orElseThrow());
            restored.finish();
            assertEquals(1, restoredSink.writer.finalFlushes);
            assertThrows(IllegalStateException.class, () -> restored.processElement("after-finish", ignored -> {}));
        } finally {
            restored.close();
        }
        assertEquals(1, firstSink.writer.closes);
        assertEquals(1, restoredSink.writer.closes);
    }

    @Test
    void incompatibleRestoredWriterStateMustFailBeforeOpeningWriter() throws Exception {
        OperatorStateBackend saved = newState(Map.of());
        saved.put("writer-0", 4, new ProbeSink(1).getWriterStateSerializer());
        ProbeSink sink = new ProbeSink(2);
        SinkWriterOperator<String> operator = new SinkWriterOperator<>(sink, environment());
        operator.initializeState(newState(saved.snapshot()));

        IOException failure = assertThrows(IOException.class, operator::open);
        assertEquals("unsupported writer state version", failure.getMessage());
        operator.close();
        assertFalse(sink.writerCreated, "A corrupted writer state must never be ignored");
    }

    @Test
    void failedFinalFlushMustPropagateAndStillAllowResourceCleanup() throws Exception {
        ProbeSink sink = new ProbeSink(1);
        sink.failFinalFlush = true;
        SinkWriterOperator<String> operator = open(sink, newState(Map.of()));
        try {
            operator.processElement("a", ignored -> {});
            IOException failure = assertThrows(IOException.class, operator::finish);
            assertEquals("final flush failed", failure.getMessage());
        } finally {
            operator.close();
        }
        assertEquals(1, sink.writer.finalFlushes);
        assertEquals(1, sink.writer.closes);
    }

    private static SinkWriterOperator<String> open(ProbeSink sink, OperatorStateBackend state) throws Exception {
        SinkWriterOperator<String> operator = new SinkWriterOperator<>(sink, environment());
        operator.initializeState(state);
        operator.open();
        return operator;
    }

    private static TaskEnvironment environment() {
        Configuration config = new Configuration();
        config.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(30));
        return new TaskEnvironment(new RuntimeTaskInfo(JobID.generate(), 27, 0, 1, 0, 128), config);
    }

    private static OperatorStateBackend newState(
            Map<String, io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot.SerializedState> values) {
        return new OperatorStateBackend(values, new RuntimeTaskInfo(JobID.generate(), 27, 0, 1, 0, 128), false);
    }

    private static final class ProbeSink implements Sink<String>, SupportsWriterState<String, Integer> {
        private final int serializerVersion;
        private boolean failCheckpointFlush;
        private boolean failFinalFlush;
        private boolean failSnapshot;
        private boolean writerCreated;
        private int lastRestoredCount = -1;
        private ProbeWriter writer;

        private ProbeSink(int serializerVersion) {
            this.serializerVersion = serializerVersion;
        }

        @Override
        public StatefulSinkWriter<String, Integer> createWriter(WriterInitContext context) {
            throw new AssertionError("Stateful Sink must restore through the state contract");
        }

        @Override
        public StatefulSinkWriter<String, Integer> restoreWriter(
                WriterInitContext context, Collection<Integer> previousState) {
            writerCreated = true;
            lastRestoredCount = previousState.stream().mapToInt(Integer::intValue).sum();
            writer = new ProbeWriter(lastRestoredCount);
            return writer;
        }

        @Override
        public SimpleVersionedSerializer<Integer> getWriterStateSerializer() {
            return new SimpleVersionedSerializer<>() {
                @Override
                public int getVersion() {
                    return serializerVersion;
                }

                @Override
                public byte[] serialize(Integer value) {
                    return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
                }

                @Override
                public Integer deserialize(int version, byte[] bytes) throws IOException {
                    if (version != serializerVersion || bytes.length != Integer.BYTES) {
                        throw new IOException("unsupported writer state version");
                    }
                    return ByteBuffer.wrap(bytes).getInt();
                }
            };
        }

        private final class ProbeWriter implements StatefulSinkWriter<String, Integer> {
            private int count;
            private int checkpointFlushes;
            private int finalFlushes;
            private int snapshots;
            private int closes;

            private ProbeWriter(int initialCount) {
                count = initialCount;
            }

            @Override
            public void write(String record) {
                count++;
            }

            @Override
            public void flush(boolean endOfInput) throws IOException {
                if (endOfInput) {
                    finalFlushes++;
                    if (failFinalFlush) {
                        throw new IOException("final flush failed");
                    }
                } else {
                    checkpointFlushes++;
                    if (failCheckpointFlush) {
                        throw new IOException("checkpoint flush failed");
                    }
                }
            }

            @Override
            public List<Integer> snapshotState(long checkpointId) throws IOException {
                snapshots++;
                if (failSnapshot) {
                    throw new IOException("writer snapshot failed");
                }
                return List.of(count);
            }

            @Override
            public void close() {
                closes++;
            }
        }
    }
}
