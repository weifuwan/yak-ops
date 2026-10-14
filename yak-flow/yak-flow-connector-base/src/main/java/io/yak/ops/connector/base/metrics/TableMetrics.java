package io.yak.ops.connector.base.metrics;

import io.yak.ops.core.data.TableId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Connector measurements shared by source mailboxes and acknowledged Sink commits.
 *
 * <p>A continuous source starts a new generation when its enumerator is restored. Callbacks
 * from closed generations are ignored. Snapshot output is fenced by the handoff checkpoint
 * before streaming records can enter the Sink, so a transaction cannot mix the two phases.
 */
public final class TableMetrics {
    private final Map<TableId, Counters> tables = new LinkedHashMap<>();
    private final Map<TableId, Counters> incremental = new LinkedHashMap<>();
    private final Set<String> finishedSplits = new HashSet<>();
    private long generation;
    private boolean restored;
    private boolean active;
    private boolean fullComplete;
    private Phase phase = Phase.INITIALIZING;
    private Long handoffCheckpointId;
    private Long completedCheckpointId;
    private long observedAt = System.currentTimeMillis();

    /** Physical source phases, independent of Task platform or presentation state. */
    public enum Phase {
        INITIALIZING,
        SNAPSHOT,
        HANDOFF,
        STREAMING
    }

    /** Registers a table before source or sink callbacks. */
    public synchronized void register(TableId table) {
        tables.computeIfAbsent(table, ignored -> new Counters());
        incremental.computeIfAbsent(table, ignored -> new Counters());
    }

    /** Records the complete bounded split count, including an empty table. */
    public synchronized void planned(TableId table, int count) {
        if (count < 0) throw new IllegalArgumentException("Negative planned split count");
        counters(tables, table).planned = count;
        touch();
    }

    /** Counts a row only after it is emitted into its output. */
    public synchronized void read(TableId table) {
        counters(tables, table).read++;
        touch();
    }

    /** Counts a bounded split after its final queued row has been emitted. */
    public synchronized void splitFinished(TableId table) {
        counters(tables, table).completed++;
        touch();
    }

    /** Counts records only after their JDBC commit has been acknowledged. */
    public synchronized void committed(TableId table, long count) {
        committed(generation, table, count);
    }

    /** Records a failed bounded read without retaining exception text or row values. */
    public synchronized void readFailed(TableId table) {
        counters(tables, table).failure = "READ_FAILED";
        touch();
    }

    /** Records an unsuccessful destination transaction in the active phase. */
    public synchronized void writeFailed(TableId table) {
        writeFailed(generation, table);
    }

    /** Returns a detached observation of bounded or initial-snapshot counters. */
    public synchronized Map<TableId, Snapshot> snapshot() {
        return snapshot(tables);
    }

    /** Starts an isolated measurement generation; restored row totals are not fabricated. */
    public synchronized long beginSnapshot(boolean fromCheckpoint) {
        generation++;
        restored = fromCheckpoint;
        active = true;
        fullComplete = false;
        phase = Phase.INITIALIZING;
        handoffCheckpointId = null;
        completedCheckpointId = null;
        tables.replaceAll((table, ignored) -> new Counters());
        incremental.replaceAll((table, ignored) -> new Counters());
        finishedSplits.clear();
        touch();
        return generation;
    }

    /** Returns the generation captured by a newly opened Reader or Writer. */
    public synchronized long generation() {
        return generation;
    }

    /** Observes an authoritative source phase, including completed-checkpoint restoration. */
    public synchronized void phase(long owner, Phase next, boolean snapshotCommitted) {
        if (owner != generation) return;
        phase = next;
        fullComplete = snapshotCommitted;
        touch();
    }

    /** Records all planned splits for one table in this generation. */
    public synchronized void planned(long owner, TableId table, int count) {
        if (owner != generation) return;
        planned(table, count);
    }

    /** Deduplicates completion events by split identity inside the current generation. */
    public synchronized void splitFinished(long owner, String splitId, TableId table) {
        if (owner != generation || !finishedSplits.add(splitId)) return;
        splitFinished(table);
    }

    /** Records emitted records separately for snapshot and continuous change processing. */
    public synchronized void read(long owner, TableId table, boolean binlog) {
        if (owner != generation) return;
        counters(binlog ? incremental : tables, table).read++;
        touch();
    }

    /** Observes source liveness from a real fetch, heartbeat or mailbox callback. */
    public synchronized void observed(long owner) {
        if (owner == generation) touch();
    }

    /** Records a completed checkpoint and whether it commits the snapshot handoff. */
    public synchronized void checkpointCompleted(long owner, long checkpointId, boolean handoff) {
        if (owner != generation) return;
        if (completedCheckpointId == null || checkpointId > completedCheckpointId) {
            completedCheckpointId = checkpointId;
        }
        if (handoff) {
            handoffCheckpointId = checkpointId;
            fullComplete = true;
        }
        touch();
    }

    /** Confirms that the Binlog Reader has received and opened its checkpoint-protected gate. */
    public synchronized void streaming(long owner) {
        if (owner != generation || generation == 0) return;
        if (!fullComplete) throw new IllegalStateException("Snapshot handoff is not committed");
        phase = Phase.STREAMING;
        touch();
    }

    /** Stops healthy-running observations while an execution generation is being closed. */
    public synchronized void sourceClosed(long owner) {
        if (owner != generation) return;
        active = false;
        touch();
    }

    /** Counts an acknowledged transaction only for the Writer's captured generation. */
    public synchronized void committed(long owner, TableId table, long count) {
        if (owner != generation) return;
        if (count < 0) throw new IllegalArgumentException("Negative committed record count");
        counters(phase == Phase.STREAMING ? incremental : tables, table).committed += count;
        touch();
    }

    /** Records a failed read in its actual phase without exposing sensitive diagnostics. */
    public synchronized void readFailed(long owner, TableId table, boolean binlog) {
        if (owner != generation) return;
        counters(binlog ? incremental : tables, table).failure = "READ_FAILED";
        touch();
    }

    /** Records a failed or ambiguous Writer transaction in its fenced source phase. */
    public synchronized void writeFailed(long owner, TableId table) {
        if (owner != generation) return;
        counters(phase == Phase.STREAMING ? incremental : tables, table).failure = "WRITE_FAILED";
        touch();
    }

    /** Captures lifecycle and all per-table values atomically; HTTP polling does not refresh sampling time. */
    public synchronized ContinuousSnapshot continuousSnapshot() {
        return new ContinuousSnapshot(
                generation,
                restored,
                active,
                fullComplete,
                phase,
                handoffCheckpointId,
                completedCheckpointId,
                observedAt,
                snapshot(tables),
                snapshot(incremental));
    }

    private static Counters counters(Map<TableId, Counters> values, TableId table) {
        return values.computeIfAbsent(table, ignored -> new Counters());
    }

    private static Map<TableId, Snapshot> snapshot(Map<TableId, Counters> counters) {
        Map<TableId, Snapshot> values = new LinkedHashMap<>();
        counters.forEach((table, value) -> values.put(
                table, new Snapshot(value.planned, value.completed, value.read, value.committed, value.failure)));
        return java.util.Collections.unmodifiableMap(values);
    }

    private void touch() {
        observedAt = System.currentTimeMillis();
    }

    /** Measured values; a null planned count means the denominator is unavailable. */
    public record Snapshot(
            Integer plannedSplits, long completedSplits, long readRows, long committedRows, String failureCode) {}

    /** Detached generation-scoped measurements; no credentials, row values or recovery cursors are included. */
    public record ContinuousSnapshot(
            long generation,
            boolean restored,
            boolean active,
            boolean fullComplete,
            Phase phase,
            Long handoffCheckpointId,
            Long completedCheckpointId,
            long observedAt,
            Map<TableId, Snapshot> snapshotTables,
            Map<TableId, Snapshot> incrementalTables) {
        public ContinuousSnapshot {
            snapshotTables = Map.copyOf(snapshotTables);
            incrementalTables = Map.copyOf(incrementalTables);
        }
    }

    private static final class Counters {
        private Integer planned;
        private long completed;
        private long read;
        private long committed;
        private String failure;
    }
}
