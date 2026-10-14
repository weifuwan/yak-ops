package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlLowWatermarkEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlSnapshotFinishedAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlSnapshotFinishedEvent;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.TableId;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Coordinates hybrid MySQL Snapshot splits and the checkpoint-gated Binlog reader.
 *
 * <p>The low watermark is emitted by the Binlog reader before any JDBC Snapshot plan
 * starts. Metadata planning and high-watermark JDBC queries run through callAsync,
 * keeping the coordinator event loop unblocked. No Binlog data reaches the Sink until
 * every Snapshot split has been emitted and a covering checkpoint has completed.
 */
public final class MySqlHybridSourceEnumerator
        implements SplitEnumerator<MySqlHybridSplit, MySqlHybridEnumeratorState> {

    private final SplitEnumeratorContext<MySqlHybridSplit> context;
    private final MySqlCdcSourceConfig config;
    private final MySqlChunkSplitter splitter;
    private final MySqlHybridSplitAssigner assigner;
    private final String fingerprint;
    private final Set<Integer> waitingReaders = new LinkedHashSet<>();
    private final boolean restoredHandoff;
    private final TableMetrics metrics;
    private final long generation;
    private final Map<String, TableId> snapshotTables = new LinkedHashMap<>();

    private boolean started;
    private boolean closed;
    private boolean planning;
    private boolean readingHigh;

    public MySqlHybridSourceEnumerator(
            SplitEnumeratorContext<MySqlHybridSplit> context,
            MySqlCdcSourceConfig config,
            String fingerprint,
            int chunkSize,
            MySqlHybridEnumeratorState restored) {
        this(context, config, fingerprint, chunkSize, restored, new TableMetrics());
    }

    /** Creates an enumerator whose measurements are fenced to this execution generation. */
    public MySqlHybridSourceEnumerator(
            SplitEnumeratorContext<MySqlHybridSplit> context,
            MySqlCdcSourceConfig config,
            String fingerprint,
            int chunkSize,
            MySqlHybridEnumeratorState restored,
            TableMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        config.schemas().keySet().forEach(metrics::register);
        generation = metrics.beginSnapshot(restored != null);
        if (restored != null) {
            boolean complete = restored.phase() == MySqlHybridEnumeratorState.Phase.HANDOFF
                    || restored.phase() == MySqlHybridEnumeratorState.Phase.STREAMING;
            TableMetrics.Phase phase = TableMetrics.Phase.INITIALIZING;
            if (restored.phase() == MySqlHybridEnumeratorState.Phase.SNAPSHOT) phase = TableMetrics.Phase.SNAPSHOT;
            if (complete) phase = TableMetrics.Phase.HANDOFF;
            metrics.phase(generation, phase, complete);
            restored.pendingSnapshots().forEach(split -> snapshotTables.put(split.splitId(), split.tableId()));
            restored.assignedSnapshots()
                    .values()
                    .forEach(split -> snapshotTables.put(split.splitId(), split.tableId()));
        }
        this.context = Objects.requireNonNull(context, "context");
        this.config = Objects.requireNonNull(config, "config");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        splitter = new MySqlChunkSplitter(config, chunkSize);
        assigner = new MySqlHybridSplitAssigner(fingerprint, restored);
        restoredHandoff = restored != null && restored.phase() == MySqlHybridEnumeratorState.Phase.HANDOFF;
        if (context.currentParallelism() < 1 || context.currentParallelism() > 16) {
            throw new IllegalArgumentException("MySQL hybrid Source supports 1 through 16 snapshot readers");
        }
    }

    @Override
    public void start() {
        if (started || closed) {
            throw new IllegalStateException("MySQL hybrid enumerator cannot start twice");
        }
        started = true;
        if (restoredHandoff) {
            // HANDOFF snapshots are restored only from completed checkpoints.
            resumeStreaming();
        } else if (assigner.phase() == MySqlHybridEnumeratorState.Phase.SNAPSHOT) {
            // The asynchronous plan/high-watermark lookup has no durable side effects.
            // A checkpoint taken while either call was in flight must restart it.
            if (!assigner.snapshotPlanned()) {
                planSnapshotAsync();
            } else if (assigner.snapshotComplete()) {
                captureHighAsync();
            }
        }
    }

    @Override
    public void handleSplitRequest(int subtaskId) {
        if (!started || closed || !context.registeredReaders().contains(subtaskId)) {
            throw new IllegalStateException("Unregistered MySQL hybrid split requester");
        }
        waitingReaders.add(subtaskId);
        assignAvailable();
    }

    @Override
    public void addReader(int subtaskId) {
        if (closed) {
            return;
        }
        if (assigner.phase() == MySqlHybridEnumeratorState.Phase.STREAMING && subtaskId == 0) {
            context.sendEventToSourceReader(0, new MySqlResumeBinlogEvent(assigner.high()));
        }
        assignAvailable();
    }

    @Override
    public void handleSourceEvent(int subtaskId, SourceEvent event) {
        if (event instanceof MySqlLowWatermarkEvent low) {
            if (subtaskId != 0) {
                throw new IllegalArgumentException("Only the Binlog owner may capture MySQL low watermark");
            }
            if (assigner.phase() == MySqlHybridEnumeratorState.Phase.BOOTSTRAP) {
                assigner.captureLow(low.offset());
                metrics.phase(generation, TableMetrics.Phase.SNAPSHOT, false);
                planSnapshotAsync();
            } else if (!assigner.low().equals(low.offset())) {
                throw new IllegalStateException("Conflicting restored MySQL low watermark");
            }
        } else if (event instanceof MySqlSnapshotFinishedEvent finished) {
            assigner.finishSnapshot(finished.splitId());
            TableId table = snapshotTables.get(finished.splitId());
            if (table != null) metrics.splitFinished(generation, finished.splitId(), table);
            context.sendEventToSourceReader(subtaskId, new MySqlSnapshotFinishedAckEvent(finished.splitId()));
            if (assigner.snapshotComplete()) {
                captureHighAsync();
            }
            assignAvailable();
        } else {
            throw new IllegalArgumentException("Unsupported MySQL hybrid source event");
        }
    }

    @Override
    public void addSplitsBack(List<MySqlHybridSplit> splits, int subtaskId) {
        if (closed || subtaskId < 0 || subtaskId >= context.currentParallelism()) {
            throw new IllegalStateException("Cannot reassign MySQL hybrid splits from this Reader");
        }
        for (MySqlHybridSplit split : splits) {
            if (split instanceof MySqlSnapshotSplit snapshot) {
                assigner.addBack(snapshot);
            } else if (split instanceof MySqlHybridBinlogSplit binlog) {
                if (subtaskId != 0) {
                    throw new IllegalArgumentException("Binlog must be owned by subtask zero");
                }
                assigner.addBack(binlog);
            }
        }
        assignAvailable();
    }

    @Override
    public MySqlHybridEnumeratorState snapshotState(long checkpointId) {
        if (!started || closed) {
            throw new IllegalStateException("Cannot checkpoint an inactive MySQL hybrid enumerator");
        }
        // Keep the preceding durable state while JDBC metadata work is asynchronous.
        // On restoration, start() reruns unfinished discovery/high-watermark capture.
        return assigner.snapshot(checkpointId);
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        boolean handoff = assigner.completeCheckpoint(checkpointId);
        metrics.checkpointCompleted(generation, checkpointId, handoff);
        if (handoff) {
            sendResumeEvent();
        }
    }

    @Override
    public void close() {
        closed = true;
        metrics.sourceClosed(generation);
        waitingReaders.clear();
    }

    private void planSnapshotAsync() {
        if (planning || assigner.snapshotPlanned()) {
            return;
        }
        planning = true;
        context.callAsync(
                () -> {
                    List<MySqlSnapshotSplit> ranges = new ArrayList<>();
                    int index = 0;
                    for (TableId table : config.schemas().keySet()) {
                        try {
                            ranges.addAll(splitter.plan(table, fingerprint, index++));
                        } catch (Exception failure) {
                            metrics.readFailed(generation, table, false);
                            throw failure;
                        }
                    }
                    return List.copyOf(ranges);
                },
                (splits, error) -> {
                    planning = false;
                    if (closed) {
                        return;
                    }
                    if (error != null) {
                        throw new IllegalStateException("MySQL hybrid snapshot chunk planning failed", error);
                    }
                    Map<TableId, Integer> counts = new LinkedHashMap<>();
                    config.schemas().keySet().forEach(table -> counts.put(table, 0));
                    for (var split : splits) {
                        snapshotTables.put(split.splitId(), split.tableId());
                        counts.merge(split.tableId(), 1, Integer::sum);
                    }
                    counts.forEach((table, count) -> metrics.planned(generation, table, count));
                    assigner.plan(splits);
                    assignAvailable();
                });
    }

    private void captureHighAsync() {
        if (readingHigh) {
            return;
        }
        readingHigh = true;
        context.callAsync(this::readHighWatermark, (high, error) -> {
            readingHigh = false;
            if (closed) {
                return;
            }
            if (error != null) {
                throw new IllegalStateException("MySQL hybrid high watermark capture failed", error);
            }
            assigner.captureHigh(high);
            metrics.phase(generation, TableMetrics.Phase.HANDOFF, false);
        });
    }

    /** Fences snapshot output before the Binlog replays from its saved low watermark. */
    private BinlogOffset readHighWatermark() throws SQLException {
        try (Connection connection = config.openSnapshotConnection();
                Statement query = connection.createStatement()) {
            ResultSet rows;
            try {
                rows = query.executeQuery("SHOW BINARY LOG STATUS");
            } catch (SQLException unsupported) {
                rows = query.executeQuery("SHOW MASTER STATUS");
            }
            try (ResultSet status = rows) {
                if (!status.next()) {
                    throw new SQLException("MySQL binary logging is disabled");
                }
                String file = status.getString(1);
                long position = status.getLong(2);
                if (file == null || file.isBlank() || position < 1) {
                    throw new SQLException("Invalid MySQL Binlog high watermark");
                }
                return new BinlogOffset(assigner.low().partition(), java.util.Map.of("file", file, "pos", position));
            }
        }
    }

    private void assignAvailable() {
        if (closed || !started) {
            return;
        }
        MySqlHybridBinlogSplit binlog = assigner.nextBinlog(0);
        if (binlog != null) {
            if (context.registeredReaders().contains(0)) {
                context.assignSplit(binlog, 0);
            } else {
                assigner.addBack(binlog);
            }
            waitingReaders.remove(0);
        }
        if (assigner.phase() != MySqlHybridEnumeratorState.Phase.SNAPSHOT || !assigner.snapshotPlanned()) {
            return;
        }
        for (int reader : List.copyOf(waitingReaders)) {
            if (!context.registeredReaders().contains(reader)) {
                waitingReaders.remove(reader);
                continue;
            }
            MySqlSnapshotSplit next = assigner.nextSnapshot();
            if (next == null) {
                return;
            }
            context.assignSplit(next, reader);
            waitingReaders.remove(reader);
        }
    }

    private void resumeStreaming() {
        // A restored HANDOFF state is necessarily read from a completed durable checkpoint.
        // The in-memory attempt has an equivalent transition after notifyCheckpointComplete.
        assigner.resumeRestoredHandoff();
        sendResumeEvent();
    }

    private void sendResumeEvent() {
        if (context.registeredReaders().contains(0)) {
            context.sendEventToSourceReader(0, new MySqlResumeBinlogEvent(assigner.high()));
        }
    }
}
