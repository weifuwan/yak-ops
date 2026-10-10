package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsStateSerializer;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checkpoint codec tests for resume positions, defensive byte copies and version validation. */
class MySqlBinlogSplitSerializerTest {

    @Test
    void retainsFineGrainedOffsetsAndHistoryWithoutSharingArrays() throws Exception {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("file", "mysql-bin.000003");
        original.put("pos", 100L);
        original.put("event", 2);
        original.put("row", 3);
        original.put("snapshot", false);
        original.put("gtids", "uuid:1-20");
        BinlogOffset offset = new BinlogOffset(Map.of("server", "yak"), original);
        byte[] history = {1, 2, 3};
        MySqlBinlogSplit split = new MySqlBinlogSplit("fingerprint", offset, history);
        history[0] = 99;

        MySqlBinlogSplitSerializer codec = new MySqlBinlogSplitSerializer();
        MySqlBinlogSplit copy = codec.deserialize(codec.getVersion(), codec.serialize(split));
        assertEquals(split.splitId(), copy.splitId());
        assertEquals(offset, copy.offset());
        assertArrayEquals(new byte[] {1, 2, 3}, copy.schemaHistory());
        assertNotSame(split.schemaHistory(), copy.schemaHistory());
        assertThrows(IOException.class, () -> codec.deserialize(99, codec.serialize(split)));
    }

    @Test
    void roundTripsPendingAndAssignedEnumeratorStates() throws Exception {
        MySqlPendingSplitsStateSerializer codec = new MySqlPendingSplitsStateSerializer();
        MySqlPendingSplitsState pending =
                new MySqlPendingSplitsState("fingerprint", false, new MySqlBinlogSplit("fingerprint", null, new byte[0]));
        MySqlPendingSplitsState restored = codec.deserialize(codec.getVersion(), codec.serialize(pending));
        assertEquals("fingerprint", restored.sourceFingerprint());
        assertEquals(false, restored.splitAssigned());
        assertEquals(null, restored.pendingSplit().offset());

        MySqlPendingSplitsState assigned = new MySqlPendingSplitsState("fingerprint", true, null);
        MySqlPendingSplitsState restoredAssigned = codec.deserialize(codec.getVersion(), codec.serialize(assigned));
        assertEquals(true, restoredAssigned.splitAssigned());
        assertEquals(null, restoredAssigned.pendingSplit());
    }

    @Test
    void rejectsIncompleteRestoredOffsetAndCorruptData() throws Exception {
        MySqlBinlogSplitSerializer codec = new MySqlBinlogSplitSerializer();
        var offset = new BinlogOffset(Map.of("server", "yak"), Map.of("pos", 5L));
        var missingHistory = new MySqlBinlogSplit("fingerprint", offset, new byte[0]);
        assertThrows(IOException.class, () -> codec.deserialize(1, codec.serialize(missingHistory)));
        assertThrows(IOException.class, () -> codec.deserialize(1, new byte[] {0, 1}));
    }
}
