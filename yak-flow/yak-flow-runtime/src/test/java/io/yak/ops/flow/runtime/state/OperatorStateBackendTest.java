package io.yak.ops.flow.runtime.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperatorStateBackendTest {

    private static final SimpleVersionedSerializer<String> STRINGS = new SimpleVersionedSerializer<>() {
        public int getVersion() { return 1; }
        public byte[] serialize(String value) { return value.getBytes(StandardCharsets.UTF_8); }
        public String deserialize(int version, byte[] bytes) throws IOException {
            if (version != 1) { throw new IOException("version mismatch"); }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    };

    @Test
    void namedStateIsVersionedAndDefensivelyCopied() throws Exception {
        var info = new RuntimeTaskInfo(JobID.generate(), 2, 0, 1, 0, 128);
        var backend = new OperatorStateBackend(Map.of(), info, false);
        backend.put("count", "10", STRINGS);
        var snapshot = backend.snapshot();
        snapshot.get("operator/count").bytes()[0] = 1;
        var restored = new OperatorStateBackend(snapshot, info, false);
        assertEquals("10", restored.get("count", STRINGS).orElseThrow());
    }

    @Test
    void changedKeySerializerVersionMustNotSilentlyBecomeMissingState() throws Exception {
        var info = new RuntimeTaskInfo(JobID.generate(), 3, 0, 1, 0, 8);
        var writer = new OperatorStateBackend(Map.of(), info, true);
        writer.putKeyed("counter", "account-1", STRINGS, "5", STRINGS);
        var restored = new OperatorStateBackend(writer.snapshot(), info, true);
        SimpleVersionedSerializer<String> upgradedKeys = new SimpleVersionedSerializer<>() {
            @Override
            public int getVersion() { return 2; }
            @Override
            public byte[] serialize(String value) { return value.getBytes(StandardCharsets.UTF_8); }
            @Override
            public String deserialize(int version, byte[] bytes) throws IOException {
                return new String(bytes, StandardCharsets.UTF_8);
            }
        };
        assertThrows(IllegalStateException.class,
                () -> restored.getKeyed("counter", "account-1", upgradedKeys, STRINGS));
        assertThrows(IllegalStateException.class,
                () -> restored.putKeyed("counter", "account-2", upgradedKeys, "6", STRINGS));
        assertEquals("5", restored.getKeyed("counter", "account-1", STRINGS, STRINGS).orElseThrow());
    }

    @Test
    void keyedStateRequiresTheOwningSubtask() throws Exception {
        var firstInfo = new RuntimeTaskInfo(JobID.generate(), 3, 0, 2, 0, 8);
        var first = new OperatorStateBackend(Map.of(), firstInfo, true);
        String localKey = null, remoteKey = null;
        for (int i = 0; i < 100; i++) {
            String key = "k" + i;
            if (KeyGroupRangeAssignment.assignKeyToParallelOperator(key, 8, 2) == 0) {
                localKey = key;
            } else {
                remoteKey = key;
            }
        }
        final String owned = localKey, remote = remoteKey;
        first.putKeyed("counter", owned, STRINGS, "17", STRINGS);
        assertEquals("17", new OperatorStateBackend(first.snapshot(), firstInfo, true)
                .getKeyed("counter", owned, STRINGS, STRINGS).orElseThrow());
        assertThrows(IllegalArgumentException.class,
                () -> first.getKeyed("counter", remote, STRINGS, STRINGS));
        assertThrows(UnsupportedOperationException.class,
                () -> new OperatorStateBackend(Map.of(), firstInfo, false)
                        .putKeyed("counter", owned, STRINGS, "17", STRINGS));
    }
}
