package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.data.TableId;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Ensures the hybrid transport persists both split kinds with complete normalized snapshot metadata. */
class MySqlSplitSerializerTest {

    @Test
    void roundTripsUnfinishedSnapshotBoundariesWithoutCursorImages() throws Exception {
        var serializer = new MySqlSplitSerializer();
        var original = new MySqlSnapshotSplit("snapshot-0", new TableId("shop", null, "orders"), 100L, 200L, null);
        MySqlSnapshotSplit restored =
                (MySqlSnapshotSplit) serializer.deserialize(serializer.getVersion(), serializer.serialize(original));
        assertEquals(original, restored);
        assertThrows(IOException.class, () -> serializer.deserialize(99, serializer.serialize(original)));
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion() + 1, serializer.serialize(original)));
        assertEquals(100L, restored.getSplitStart()[0]);
        assertEquals(200L, restored.getSplitEnd()[0]);
        assertEquals(null, restored.output());
    }

    @Test
    void roundTripsCompletedSnapshotWithBoundedBackfillSchemaHistory() throws Exception {
        var table = new TableId("shop", null, "orders");
        var high = new DebeziumOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000010", "pos", 300L));
        var finished = new FinishedSnapshotSplitInfo(
                table, "snapshot-history", null, null, high);
        var original = new MySqlSnapshotSplit(
                "snapshot-history", table, null, null, null, null, 0, finished, null,
                new byte[] {1, 2, 3});
        var serializer = MySqlSplitSerializer.INSTANCE;
        var restored = (MySqlSnapshotSplit) serializer.deserialize(
                serializer.getVersion(), serializer.serialize(original));
        assertEquals(original, restored);
        assertArrayEquals(new byte[] {1, 2, 3}, restored.finishedSchemaHistory());
        byte[] detached = restored.finishedSchemaHistory();
        detached[0] = 99;
        assertArrayEquals(new byte[] {1, 2, 3}, restored.finishedSchemaHistory());
    }

    @Test
    void incompleteBinlogMetadataSurvivesReaderCheckpoint() throws Exception {
        var high = new DebeziumOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000010", "pos", 300L));
        var incomplete = MySqlBinlogSplit.forCompletedSnapshot(
                high, new byte[] {1, 2, 3}, high, java.util.List.of(), 3);
        var serializer = MySqlSplitSerializer.INSTANCE;
        MySqlBinlogSplit restored = (MySqlBinlogSplit) serializer.deserialize(
                serializer.getVersion(), serializer.serialize(incomplete));
        assertEquals(3, restored.getTotalFinishedSplitSize());
        assertEquals(0, restored.finishedSplits().size());
        org.junit.jupiter.api.Assertions.assertFalse(restored.isCompletedSplit());
    }

    @Test
    void roundTripsPausedAndRunningBinlogWithSchemaHistory() throws Exception {
        var serializer = new MySqlSplitSerializer();
        var low = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000010", "pos", 100L));
        var high = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000010", "pos", 300L));
        var bootstrap = MySqlBinlogSplit.forInitialSnapshot();
        var paused = bootstrap.withAnchor(low, new byte[] {1, 2, 3});
        var running = paused.startStreaming(high, completed(high)).withEmittedOffset(high, new byte[] {1, 2, 3});
        var restored = (MySqlBinlogSplit) serializer.deserialize(serializer.getVersion(), serializer.serialize(running));
        assertEquals(MySqlBinlogSplit.Phase.STREAMING, restored.phase());
        assertEquals(high, restored.highWatermark());
        assertEquals(high, restored.offset());
        assertArrayEquals(new byte[] {1, 2, 3}, restored.schemaHistory());
        assertArrayEquals(serializer.serialize(running), serializer.serialize(restored));
        assertThrows(IllegalStateException.class, () -> paused.withEmittedOffset(high, new byte[] {1}));
    }

    @Test
    void extendsFinishedSnapshotFencesWithoutRewindingThePausedBinlogOffset() {
        var low = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000010", "pos", 100L));
        var oldHigh = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000010", "pos", 200L));
        var current = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000010", "pos", 250L));
        var newHigh = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000010", "pos", 300L));
        var orders = new FinishedSnapshotSplitInfo(new TableId("shop", null, "orders"), "orders-0", null, null, oldHigh);
        var items = new FinishedSnapshotSplitInfo(new TableId("shop", null, "items"), "items-0", null, null, newHigh);
        byte[] history = new byte[] {1, 2, 3};

        var running = MySqlBinlogSplit.forInitialSnapshot()
                .withAnchor(low, history)
                .startStreaming(oldHigh, java.util.List.of(orders))
                .withEmittedOffset(current, history);
        var paused = MySqlBinlogSplit.toSuspendedBinlogSplit(running);
        var resumed = paused.startStreaming(newHigh, java.util.List.of(orders, items));

        assertEquals(MySqlBinlogSplit.Phase.STREAMING, resumed.phase());
        assertEquals(current, resumed.offset(), "Metadata updates must preserve the live Binlog offset");
        assertEquals(newHigh, resumed.highWatermark());
        assertEquals(java.util.List.of(orders, items), resumed.finishedSplits());
        assertArrayEquals(history, resumed.schemaHistory());
    }

    @Test
    void rejectsTruncatedOrUnknownSplitKind() {
        var serializer = new MySqlSplitSerializer();
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), new byte[] {9}));
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), new byte[] {1}));
    }

    private static java.util.List<FinishedSnapshotSplitInfo> completed(DebeziumOffset high) {
        return java.util.List.of(new FinishedSnapshotSplitInfo(
                new io.yak.ops.core.data.TableId("shop", null, "orders"), "chunk", null, null, high));
    }
    /** The repeated-serialization contract from Flink CDC's MySqlSplitSerializerTest. */
    @Test
    void testRepeatedSerializationCache() throws Exception {
        var serializer = MySqlSplitSerializer.INSTANCE;
        MySqlSnapshotSplit snapshot = new MySqlSnapshotSplit(
                "repeated", new TableId("shop", null, "orders"), null, 100L, null);
        byte[] original = serializer.serialize(snapshot);
        for (int index = 0; index < 4; index++) {
            assertArrayEquals(original, serializer.serialize(snapshot));
            assertArrayEquals(
                    original,
                    serializer.serialize(
                            serializer.deserialize(serializer.getVersion(), original)));
        }
    }

    @Test
    void testSplitVersionAndDefensiveSerializedFormCache() throws Exception {
        var serializer = MySqlSplitSerializer.INSTANCE;
        assertEquals(3, serializer.getVersion());
        var split = new MySqlSnapshotSplit(
                "cached-0", new TableId("shop", null, "orders"), null, 12L, null);
        byte[] baseline = serializer.serialize(split);
        byte[] corruptedCopy = serializer.serialize(split);
        corruptedCopy[0] ^= 0x7F;
        assertArrayEquals(baseline, serializer.serialize(split));
        assertEquals(split, serializer.deserializeSplit(serializer.getVersion(), baseline));
        assertThrows(IOException.class, () -> serializer.deserializeSplit(10, baseline));
    }

    @Test
    void classConversionPreservesTheCurrentTypedSplitCheckpointContract() throws Exception {
        TableId TABLE = new TableId("shop", null, "orders");
        ChunkRange range = new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, 100L, 200L);
        MySqlSnapshotSplit split = MySqlSnapshotSplit.typed("orders:0", range);
        MySqlSplitSerializer serializer = new MySqlSplitSerializer();
        byte[] serialized = serializer.serialize(split);
        MySqlSnapshotSplit restored =
                (MySqlSnapshotSplit) serializer.deserialize(serializer.getVersion(), serialized);
        assertEquals(split, restored);
        assertArrayEquals(split.getSplitStart(), restored.getSplitStart());
        assertArrayEquals(split.getSplitEnd(), restored.getSplitEnd());
    }
}
