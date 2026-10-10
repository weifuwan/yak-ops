package io.yak.ops.connector.cdc.mysql.source.offset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
            Map<String, List<GtidInterval>> leftSet = parseGtids(leftGtids);
            Map<String, List<GtidInterval>> rightSet = parseGtids(rightGtids);
            if (!leftSet.equals(rightSet)) {
                if (isContainedWithin(leftSet, rightSet)) {
                    return -1;
                }
                if (isContainedWithin(rightSet, leftSet)) {
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

    /**
     * Normalizes comma-separated MySQL UUID:interval GTID sets.
     *
     * <p>Parsing here avoids binding checkpoint correctness to a Debezium-internal
     * implementation that moved between connector versions.
     */
    private static Map<String, List<GtidInterval>> parseGtids(String encoded) {
        Map<String, List<GtidInterval>> ranges = new LinkedHashMap<>();
        for (String server : encoded.split(",", -1)) {
            String[] pieces = server.trim().split(":", -1);
            if (pieces.length < 2 || pieces[0].isBlank()) {
                throw new IllegalArgumentException("Invalid MySQL GTID set");
            }
            List<GtidInterval> intervals = ranges.computeIfAbsent(
                    pieces[0].toLowerCase(Locale.ROOT), ignored -> new ArrayList<>());
            for (int i = 1; i < pieces.length; i++) {
                String[] bounds = pieces[i].split("-", -1);
                if (bounds.length < 1 || bounds.length > 2) {
                    throw new IllegalArgumentException("Invalid MySQL GTID interval");
                }
                long first = Long.parseLong(bounds[0]);
                long last = bounds.length == 2 ? Long.parseLong(bounds[1]) : first;
                intervals.add(new GtidInterval(first, last));
            }
        }
        Map<String, List<GtidInterval>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<GtidInterval>> server : ranges.entrySet()) {
            List<GtidInterval> sorted = new ArrayList<>(server.getValue());
            sorted.sort(Comparator.comparingLong(GtidInterval::first));
            List<GtidInterval> merged = new ArrayList<>();
            for (GtidInterval current : sorted) {
                if (!merged.isEmpty()) {
                    GtidInterval previous = merged.getLast();
                    if (current.first() <= previous.last()
                            || (previous.last() < Long.MAX_VALUE && current.first() == previous.last() + 1)) {
                        merged.set(merged.size() - 1, new GtidInterval(
                                previous.first(), Math.max(previous.last(), current.last())));
                        continue;
                    }
                }
                merged.add(current);
            }
            normalized.put(server.getKey(), List.copyOf(merged));
        }
        return Map.copyOf(normalized);
    }

    private static boolean isContainedWithin(
            Map<String, List<GtidInterval>> candidate, Map<String, List<GtidInterval>> enclosing) {
        for (Map.Entry<String, List<GtidInterval>> server : candidate.entrySet()) {
            List<GtidInterval> available = enclosing.get(server.getKey());
            if (available == null) {
                return false;
            }
            for (GtidInterval interval : server.getValue()) {
                if (available.stream()
                        .noneMatch(range -> range.first() <= interval.first() && range.last() >= interval.last())) {
                    return false;
                }
            }
        }
        return true;
    }

    private record GtidInterval(long first, long last) {
        private GtidInterval {
            if (first < 1 || last < first) {
                throw new IllegalArgumentException("Invalid MySQL GTID sequence numbers");
            }
        }
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
