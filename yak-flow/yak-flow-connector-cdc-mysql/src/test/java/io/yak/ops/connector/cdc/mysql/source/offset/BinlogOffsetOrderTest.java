package io.yak.ops.connector.cdc.mysql.source.offset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Exercises finer-grained MySQL offsets before using them for snapshot fences. */
class BinlogOffsetOrderTest {

    @Test
    void comparesFilePositionsAndEventRows() {
        BinlogOffset low = offset("mysql-bin.000001", 100L, 0, 0);
        BinlogOffset sameEvent = offset("mysql-bin.000001", 100L, 0, 1);
        BinlogOffset high = offset("mysql-bin.000001", 200L, 0, 0);
        BinlogOffset rotated = offset("mysql-bin.000002", 4L, 0, 0);
        assertTrue(BinlogOffsetOrder.isAfter(sameEvent, low));
        assertTrue(BinlogOffsetOrder.isAfter(high, sameEvent));
        assertTrue(BinlogOffsetOrder.isAfter(rotated, high));
        assertFalse(BinlogOffsetOrder.isAfter(low, high));
        assertEquals(0, BinlogOffsetOrder.compare(high, high));
    }

    @Test
    void comparesContainedGtidHistoriesWithoutLosingWithinTransactionProgress() {
        String source = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        BinlogOffset first = new BinlogOffset(
                Map.of("server", "shop"),
                Map.of("gtids", source + ":1-5", "file", "mysql-bin.000001", "pos", 100L, "row", 0));
        BinlogOffset later = new BinlogOffset(
                Map.of("server", "shop"),
                Map.of("gtids", source + ":1-7", "file", "mysql-bin.000001", "pos", 120L, "row", 0));
        assertTrue(BinlogOffsetOrder.isAfter(later, first));
    }

    @Test
    void rejectsCrossSourceOffsetsAndUnknownPositions() {
        BinlogOffset regular = offset("mysql-bin.000001", 100L, 0, 0);
        BinlogOffset otherServer = new BinlogOffset(
                Map.of("server", "another"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        assertThrows(IllegalArgumentException.class, () -> BinlogOffsetOrder.compare(regular, otherServer));
        BinlogOffset missingFile = new BinlogOffset(
                Map.of("server", "shop"), Map.of("pos", 100L));
        assertThrows(IllegalArgumentException.class, () -> BinlogOffsetOrder.compare(regular, missingFile));
    }

    private static BinlogOffset offset(String file, long position, int event, int row) {
        return new BinlogOffset(
                Map.of("server", "shop"),
                Map.of("file", file, "pos", position, "event", event, "row", row));
    }
}
