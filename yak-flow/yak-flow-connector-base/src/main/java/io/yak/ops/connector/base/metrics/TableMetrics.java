package io.yak.ops.connector.base.metrics;

import io.yak.ops.core.data.TableId;
import java.util.LinkedHashMap;
import java.util.Map;

/** Attempt-local table counters shared by source mailboxes and acknowledged Sink commits. */
public final class TableMetrics {
    private final Map<TableId, Counters> tables = new LinkedHashMap<>();

    /** Registers a table before any asynchronous source or sink callbacks. */
    public synchronized void register(TableId table) {
        tables.computeIfAbsent(table, ignored -> new Counters());
    }

    /** Records the complete split count for one table, including an empty table. */
    public synchronized void planned(TableId table, int count) {
        if (count < 0) throw new IllegalArgumentException("Negative planned split count");
        Counters value = counters(table);
        value.planned = count;
    }

    /** Counts a row only after it is emitted from the reader into its output. */
    public synchronized void read(TableId table) {
        counters(table).read++;
    }

    /** Counts a split only after its final queued row was emitted by the owning mailbox. */
    public synchronized void splitFinished(TableId table) {
        counters(table).completed++;
    }

    /** Counts input records only after their JDBC transaction commit was acknowledged. */
    public synchronized void committed(TableId table, long count) {
        if (count < 0) throw new IllegalArgumentException("Negative committed record count");
        counters(table).committed += count;
    }

    /** Records a failed read without retaining exception text, credentials or row values. */
    public synchronized void readFailed(TableId table) {
        counters(table).failure = "READ_FAILED";
    }

    /** Records an unsuccessful or ambiguous destination transaction. */
    public synchronized void writeFailed(TableId table) {
        counters(table).failure = "WRITE_FAILED";
    }

    /** Returns a detached, consistent observation of all counters. */
    public synchronized Map<TableId, Snapshot> snapshot() {
        Map<TableId, Snapshot> values = new LinkedHashMap<>();
        tables.forEach((table, value) -> values.put(
                table, new Snapshot(value.planned, value.completed, value.read, value.committed, value.failure)));
        return java.util.Collections.unmodifiableMap(values);
    }

    private Counters counters(TableId table) {
        return tables.computeIfAbsent(table, ignored -> new Counters());
    }

    /** Non-secret measurement values; a null planned count means planning has not been observed. */
    public record Snapshot(
            Integer plannedSplits, long completedSplits, long readRows, long committedRows, String failureCode) {}

    private static final class Counters {
        private Integer planned;
        private long completed;
        private long read;
        private long committed;
        private String failure;
    }
}
