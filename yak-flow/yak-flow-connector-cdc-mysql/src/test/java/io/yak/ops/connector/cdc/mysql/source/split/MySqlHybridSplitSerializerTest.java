package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.core.data.TableId;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Ensures the hybrid transport persists both split kinds without changing PR1 codec state. */
class MySqlHybridSplitSerializerTest {

    @Test
    void roundTripsCursorAndBoundaries() throws Exception {
        var codec = new MySqlHybridSplitSerializer();
        var original = new MySqlSnapshotSplit(
                "snapshot-0", "fingerprint", new TableId("shop", null, "orders"), 100L, 200L, 143L);
        MySqlSnapshotSplit restored =
                (MySqlSnapshotSplit) codec.deserialize(codec.getVersion(), codec.serialize(original));
        assertEquals(original, restored);
        assertThrows(IOException.class, () -> codec.deserialize(99, codec.serialize(original)));
        assertEquals(144L, restored.withLastEmittedKey(144L).lastEmittedKey());
        assertThrows(IllegalArgumentException.class, () -> restored.withLastEmittedKey(143L));
    }

    @Test
    void roundTripsPausedAndRunningBinlogWithSchemaHistory() throws Exception {
        var codec = new MySqlHybridSplitSerializer();
        var low = new BinlogOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000010", "pos", 100L));
        var high = new BinlogOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000010", "pos", 300L));
        var bootstrap = new MySqlHybridBinlogSplit(
                new MySqlBinlogSplit("fingerprint", null, new byte[0]),
                MySqlHybridBinlogSplit.Phase.BOOTSTRAP,
                null);
        var paused = bootstrap.withAnchor(low, new byte[] {1, 2, 3});
        var running = paused.startStreaming(high).withEmittedOffset(low, new byte[] {1, 2, 3});
        var restored = (MySqlHybridBinlogSplit) codec.deserialize(codec.getVersion(), codec.serialize(running));
        assertEquals(MySqlHybridBinlogSplit.Phase.STREAMING, restored.phase());
        assertEquals(high, restored.highWatermark());
        assertEquals(low, restored.binlog().offset());
        assertArrayEquals(new byte[] {1, 2, 3}, restored.binlog().schemaHistory());
        assertThrows(IllegalStateException.class, () -> paused.withEmittedOffset(high, new byte[] {1}));
    }

    @Test
    void rejectsTruncatedOrUnknownSplitKind() {
        var codec = new MySqlHybridSplitSerializer();
        assertThrows(IOException.class, () -> codec.deserialize(1, new byte[] {9}));
        assertThrows(IOException.class, () -> codec.deserialize(1, new byte[] {1}));
    }
}
