package io.yak.ops.cdc.runtime.api.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.runtime.api.serialization.SimpleVersionedSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class SinkContractTest {
    @Test
    void committingWriterPreparesDataAfterFlush() throws Exception {
        TestWriter writer = new TestWriter();
        SinkWriter.Context context = new SinkWriter.Context() {
            @Override public long currentWatermark() { return Long.MIN_VALUE; }
            @Override public Long timestamp() { return null; }
        };
        writer.write(10, context);
        writer.write(20, context);
        writer.flush(false);
        assertEquals(List.of(10, 20), new ArrayList<>(writer.prepareCommit()));
        writer.close();
        assertTrue(writer.closed);
    }

    @Test
    void serializerUsesExplicitStateVersion() throws Exception {
        SimpleVersionedSerializer<Integer> serializer = new SimpleVersionedSerializer<Integer>() {
            @Override public int getVersion() { return 1; }
            @Override public byte[] serialize(Integer input) { return ByteBuffer.allocate(4).putInt(input).array(); }
            @Override public Integer deserialize(int version, byte[] data) throws IOException {
                if (version != 1) { throw new IOException("Unsupported version: " + version); }
                return ByteBuffer.wrap(data).getInt();
            }
        };
        assertEquals(42, serializer.deserialize(serializer.getVersion(), serializer.serialize(42)));
    }

    private static final class TestWriter implements CommittingSinkWriter<Integer, Integer> {
        private final List<Integer> pending = new ArrayList<>();
        private boolean closed;

        @Override
        public void write(Integer value, Context context) {
            pending.add(value);
        }

        @Override
        public void flush(boolean endOfInput) {}

        @Override
        public Collection<Integer> prepareCommit() {
            return List.copyOf(pending);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
