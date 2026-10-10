package io.yak.ops.connector.cdc.mysql.source.offset;

import io.debezium.connector.mysql.GtidSet;
import java.util.Map;
import java.util.Objects;

/**
 * Orders two MySQL Binlog cursors for bounded backfill and finished-chunk filtering.
 *
 * <p>Follows Flink CDC's GTID-set, file/position and within-event row ordering. Unlike
 * comparing raw offset maps, incomparable GTID sets fail rather than silently imposing
 * an arbitrary direction. All positions must refer to the same Debezium source partition.
 */
public final class BinlogOffsetOrder {

    private BinlogOffsetOrder() {}

    /**
     * Compares checkpoints using fine-grained completed-event and row positions.
     *
     * @throws IllegalArgumentException if cursors are from different source partitions or
     *         contain missing/ambiguous MySQL Binlog position information
     */
    public static int compare(BinlogOffset left, BinlogOffset right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (!left.partition().equals(right.partition())) {
            throw new IllegalArgumentException("Cannot order offsets from different MySQL source partitions");
        }

        String leftGtids = text(left.position(), "gtids");
        String rightGtids = text(right.position(), "gtids");
        if (leftGtids != null && rightGtids != null) {
            GtidSet leftSet = new GtidSet(leftGtids);
            GtidSet rightSet = new GtidSet(rightGtids);
            if (!leftSet.equals(rightSet)) {
                if (leftSet.isContainedWithin(rightSet)) {
                    return -1;
                }
                if (rightSet.isContainedWithin(leftSet)) {
                    return 1;
                }
                throw new IllegalArgumentException("Unordered MySQL GTID histories");
            }
            return compareEventRow(left.position(), right.position());
        }

        String leftFile = text(left.position(), "file");
        String rightFile = text(right.position(), "file");
        if (leftFile == null || rightFile == null) {
            throw new IllegalArgumentException("Comparable MySQL offsets require file positions or GTID sets");
        }
        int file = leftFile.compareToIgnoreCase(rightFile);
        if (file != 0) {
            return Integer.signum(file);
        }
        int position = Long.compare(number(left.position(), "pos"), number(right.position(), "pos"));
        return position != 0 ? position : compareEventRow(left.position(), right.position());
    }

    /** True when a Binlog event has passed the normalized snapshot chunk's High watermark. */
    public static boolean isAfter(BinlogOffset event, BinlogOffset high) {
        return compare(event, high) > 0;
    }

    private static int compareEventRow(Map<String, Object> left, Map<String, Object> right) {
        int event = Long.compare(number(left, "event"), number(right, "event"));
        return event != 0 ? event : Long.compare(number(left, "row"), number(right, "row"));
    }

    private static String text(Map<String, Object> position, String key) {
        Object value = position.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static long number(Map<String, Object> position, String key) {
        Object value = position.get(key);
        if (value == null) {
            return 0L;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Non-numeric MySQL offset component: " + key);
        }
        return number.longValue();
    }
}
