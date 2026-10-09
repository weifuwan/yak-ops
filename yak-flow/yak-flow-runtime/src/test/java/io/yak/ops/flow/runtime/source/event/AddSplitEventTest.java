package io.yak.ops.flow.runtime.source.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.flow.runtime.support.TestSplitSerializers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AddSplitEventTest {

    private record Split(String splitId) implements SourceSplit {}

    @Test
    void shouldSerializeSplitsImmediatelyAndDeserializeWithTheirOriginalVersion() throws Exception {
        byte[] reusable = "original".getBytes(StandardCharsets.UTF_8);
        SimpleVersionedSerializer<Split> serializer = new SimpleVersionedSerializer<>() {
            @Override
            public int getVersion() {
                return 3;
            }

            @Override
            public byte[] serialize(Split split) {
                return reusable;
            }

            @Override
            public Split deserialize(int version, byte[] bytes) throws IOException {
                if (version != 3) {
                    throw new IOException("Unknown split serializer version");
                }
                return new Split(new String(bytes, StandardCharsets.UTF_8));
            }
        };
        AddSplitEvent<Split> event = new AddSplitEvent<>(List.of(new Split("original")), serializer);
        reusable[0] = 'X';

        assertEquals(3, event.serializerVersion());
        assertEquals(1, event.splitCount());
        assertEquals(List.of(new Split("original")), event.splits(serializer));
        assertThrows(IOException.class, () -> event.splits(new SimpleVersionedSerializer<>() {
            @Override
            public int getVersion() {
                return 4;
            }

            @Override
            public byte[] serialize(Split split) {
                return split.splitId().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Split deserialize(int version, byte[] bytes) throws IOException {
                if (version != 4) {
                    throw new IOException("Version mismatch");
                }
                return new Split(new String(bytes, StandardCharsets.UTF_8));
            }
        }));
    }

    @Test
    void shouldRejectMalformedSplitAssignments() {
        var serializer = TestSplitSerializers.utf8(Split::splitId, Split::new);
        assertThrows(IllegalArgumentException.class, () -> new AddSplitEvent<>(List.<Split>of(), serializer));
        assertThrows(IllegalArgumentException.class,
                () -> new AddSplitEvent<>(List.of(new Split("")), serializer));
        assertThrows(NullPointerException.class, () -> new AddSplitEvent<>(null, serializer));
    }
}
