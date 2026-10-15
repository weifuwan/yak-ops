package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MySqlHybridBinlogSplitTest {

    @Test
    void transitionsReturnIndependentStateWithoutMutatingCheckpointHistory() {
        byte[] originalHistory = {1, 2, 3};
        BinlogOffset low = offset(100L);
        BinlogOffset high = offset(300L);
        BinlogOffset emitted = offset(400L);
        MySqlHybridBinlogSplit bootstrap = new MySqlHybridBinlogSplit(
                new MySqlBinlogSplit("fingerprint", null, new byte[0]),
                MySqlHybridBinlogSplit.Phase.BOOTSTRAP,
                null);

        MySqlHybridBinlogSplit paused = bootstrap.withAnchor(low, originalHistory);
        originalHistory[0] = 99;
        assertEquals(MySqlHybridBinlogSplit.Phase.BOOTSTRAP, bootstrap.phase());
        assertEquals(MySqlHybridBinlogSplit.Phase.PAUSED, paused.phase());
        assertEquals(low, paused.binlog().offset());
        assertArrayEquals(new byte[] {1, 2, 3}, paused.binlog().schemaHistory());

        byte[] exposed = paused.binlog().schemaHistory();
        exposed[0] = 88;
        assertArrayEquals(new byte[] {1, 2, 3}, paused.binlog().schemaHistory());

        MySqlHybridBinlogSplit running = paused.startStreaming(high, completed(high));
        assertEquals(MySqlHybridBinlogSplit.Phase.STREAMING, running.phase());
        assertEquals(high, running.highWatermark());
        assertNotSame(paused.binlog(), running.binlog());

        MySqlHybridBinlogSplit progressed = running.withEmittedOffset(emitted, new byte[] {4, 5});
        assertNotSame(running, progressed);
        assertEquals(high, running.binlog().offset());
        assertEquals(emitted, progressed.binlog().offset());
        assertArrayEquals(new byte[] {4, 5}, progressed.binlog().schemaHistory());
        assertEquals(high, progressed.highWatermark());
    }

    @Test
    void splitUsesIdentitySemanticsAndHidesCheckpointDetails() {
        MySqlBinlogSplit binlog = new MySqlBinlogSplit("secret-definition", offset(100L), new byte[] {1});
        MySqlHybridBinlogSplit first =
                new MySqlHybridBinlogSplit(binlog, MySqlHybridBinlogSplit.Phase.PAUSED, null);
        MySqlHybridBinlogSplit second =
                new MySqlHybridBinlogSplit(binlog, MySqlHybridBinlogSplit.Phase.PAUSED, null);

        assertEquals(MySqlBinlogSplit.ID, first.splitId());
        assertNotEquals(first, second);
        assertFalse(first.getClass().isRecord());

        String diagnostic = first.toString();
        assertTrue(diagnostic.contains("PAUSED"));
        assertFalse(diagnostic.contains("secret-definition"));
        assertFalse(diagnostic.contains("sensitive-binlog-position"));
    }

    @Test
    void invalidPhaseTransitionsRemainRejected() {
        MySqlHybridBinlogSplit bootstrap = new MySqlHybridBinlogSplit(
                new MySqlBinlogSplit("fingerprint", null, new byte[0]),
                MySqlHybridBinlogSplit.Phase.BOOTSTRAP,
                null);
        assertThrows(IllegalStateException.class, () -> bootstrap.startStreaming(offset(100L), completed(offset(100L))));
        assertThrows(IllegalArgumentException.class, () -> new MySqlHybridBinlogSplit(
                bootstrap.binlog(), MySqlHybridBinlogSplit.Phase.PAUSED, null));

        MySqlHybridBinlogSplit paused = bootstrap.withAnchor(offset(100L), new byte[] {1, 2});
        assertThrows(IllegalStateException.class, () -> paused.withEmittedOffset(offset(200L), new byte[] {1}));
        assertThrows(IllegalStateException.class, () -> paused.withAnchor(offset(200L), new byte[] {1}));
    }

    private static BinlogOffset offset(long position) {
        return new BinlogOffset(
                Map.of("server", "secret-server"),
                Map.of("file", "sensitive-binlog-position", "pos", position));
    }

    private static java.util.List<FinishedSnapshotSplitInfo> completed(BinlogOffset high) {
        return java.util.List.of(new FinishedSnapshotSplitInfo(
                new io.yak.ops.core.data.TableId("shop", null, "orders"), "chunk", null, null, high));
    }

    @org.junit.jupiter.api.Test
    void refusesStreamingStateWithoutMatchingCompletedChunkMetadata() {
        var binlog = new MySqlBinlogSplit("fp", offset(200L), new byte[]{1});
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MySqlHybridBinlogSplit(binlog, MySqlHybridBinlogSplit.Phase.STREAMING, offset(200L)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MySqlHybridBinlogSplit(binlog, MySqlHybridBinlogSplit.Phase.STREAMING,
                        offset(300L), completed(offset(200L))));
    }
}
