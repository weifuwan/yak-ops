package io.yak.ops.flow.runtime.operators.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.connector.sink.Committer;
import io.yak.ops.core.api.connector.sink.CommittingSinkWriter;
import io.yak.ops.core.api.connector.sink.SupportsCommitter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.execution.TaskEnvironment;
import io.yak.ops.flow.runtime.state.OperatorStateBackend;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Prepared transactions are reconciled against durable committables without opening a new Writer. */
class SinkCommitRecoveryTest {

    private static final SimpleVersionedSerializer<Long> SERIALIZER = new SimpleVersionedSerializer<>() {
        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public byte[] serialize(Long value) {
            return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
        }

        @Override
        public Long deserialize(int version, byte[] bytes) throws IOException {
            if (version != 1 || bytes.length != Long.BYTES) {
                throw new IOException("Invalid committable");
            }
            return ByteBuffer.wrap(bytes).getLong();
        }
    };

    @Test
    void emptyCheckpointRollsBackOwnedOrphansWithoutCreatingWriter() throws Exception {
        RecordingSink sink = new RecordingSink();
        TaskEnvironment context = context();
        var backend = new OperatorStateBackend(java.util.Map.of(), context.taskInfo(), false);

        new SinkWriterOperator<>(sink, context).recoverCommittables(backend);

        assertEquals(List.of(List.of()), sink.recovered);
        assertEquals(List.of(), sink.committed);
        assertEquals(0, sink.writerCreations);
    }

    @Test
    void publishedCommittableIsPreservedAndCommittedOnRecovery() throws Exception {
        RecordingSink sink = new RecordingSink();
        TaskEnvironment context = context();
        var backend = new OperatorStateBackend(java.util.Map.of(), context.taskInfo(), false);
        backend.put("committable-0", 42L, SERIALIZER);

        new SinkWriterOperator<>(sink, context).recoverCommittables(backend);

        assertEquals(List.of(List.of(42L)), sink.recovered);
        assertEquals(List.of(List.of(42L)), sink.committed);
        assertEquals(0, sink.writerCreations);
    }

    @Test
    void corruptedCommittableFailsBeforeAnyRecoveryOrCommit() throws Exception {
        RecordingSink sink = new RecordingSink();
        TaskEnvironment context = context();
        var backend = new OperatorStateBackend(java.util.Map.of(), context.taskInfo(), false);
        backend.put("committable-0", 42L, SERIALIZER);
        backend.remove("committable-0");
        backend.put("committable-1", 42L, SERIALIZER);

        assertThrows(IllegalStateException.class,
                () -> new SinkWriterOperator<>(sink, context).recoverCommittables(backend));

        assertEquals(List.of(), sink.recovered);
        assertEquals(List.of(), sink.committed);
    }

    private static TaskEnvironment context() {
        return new TaskEnvironment(
                new RuntimeTaskInfo(JobID.generate(), 1, 0, 1, 0, 128), new Configuration());
    }

    private static final class RecordingSink implements SupportsCommitter<String, Long> {
        private final List<List<Long>> recovered = new ArrayList<>();
        private final List<List<Long>> committed = new ArrayList<>();
        private int writerCreations;

        @Override
        public CommittingSinkWriter<String, Long> createWriter(WriterInitContext context) {
            writerCreations++;
            throw new AssertionError("Transaction reconciliation must not open a Writer");
        }

        @Override
        public Committer<Long> createCommitter(WriterInitContext context) {
            return new Committer<>() {
                @Override
                public void commit(Collection<Long> committables, boolean recoveredFromCheckpoint) {
                    if (!recoveredFromCheckpoint) throw new AssertionError("Expected durable recovery");
                    committed.add(List.copyOf(committables));
                }

                @Override
                public void recover(Collection<Long> keep) {
                    recovered.add(List.copyOf(keep));
                }

                @Override
                public void close() {}
            };
        }

        @Override
        public SimpleVersionedSerializer<Long> getCommittableSerializer() {
            return SERIALIZER;
        }
    }
}
