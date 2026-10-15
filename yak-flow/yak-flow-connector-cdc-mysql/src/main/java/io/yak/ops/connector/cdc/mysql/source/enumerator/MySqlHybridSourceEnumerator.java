package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlLowWatermarkEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlSnapshotFinishedAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlSnapshotFinishedEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlStopSnapshotEvent;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffsetOrder;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.TableId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Coordinates bounded progressive chunk discovery, durable completion metadata, and the Binlog handoff. */
public final class MySqlHybridSourceEnumerator
        implements SplitEnumerator<MySqlHybridSplit, MySqlHybridEnumeratorState> {
    private static final int MAX_PENDING_CHUNKS = 32;
    private final SplitEnumeratorContext<MySqlHybridSplit> context;
    private final MySqlChunkSplitter splitter;
    private final MySqlHybridSplitAssigner assigner;
    private final String fingerprint;
    private final List<TableId> tables;
    private final Set<Integer> waitingReaders = new LinkedHashSet<>();
    private final TableMetrics metrics;
    private final long generation;
    private final boolean restoredHandoff;
    private boolean started;
    private boolean closed;
    private boolean planning;

    public MySqlHybridSourceEnumerator(
            SplitEnumeratorContext<MySqlHybridSplit> context,
            MySqlCdcSourceConfig config,
            String fingerprint,
            int chunkSize,
            MySqlHybridEnumeratorState restored) {
        this(context, config, fingerprint, chunkSize, restored, new TableMetrics());
    }

    public MySqlHybridSourceEnumerator(
            SplitEnumeratorContext<MySqlHybridSplit> context,
            MySqlCdcSourceConfig config,
            String fingerprint,
            int chunkSize,
            MySqlHybridEnumeratorState restored,
            TableMetrics metrics) {
        this.context = Objects.requireNonNull(context, "context");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        tables = List.copyOf(config.schemas().keySet());
        tables.forEach(metrics::register);
        generation = metrics.beginSnapshot(restored != null);
        splitter = new MySqlChunkSplitter(config, chunkSize);
        assigner = new MySqlHybridSplitAssigner(fingerprint, restored);
        restoredHandoff = restored != null && restored.phase() == MySqlHybridEnumeratorState.Phase.HANDOFF;
        if (context.currentParallelism() < 1
                || context.currentParallelism() > 16
                || assigner.nextTableIndex() > tables.size()) {
            throw new IllegalArgumentException("Invalid MySQL Hybrid parallelism or discovery position");
        }
        if (restored != null) {
            boolean complete = restoredHandoff || restored.phase() == MySqlHybridEnumeratorState.Phase.STREAMING;
            metrics.phase(
                    generation,
                    complete
                            ? TableMetrics.Phase.HANDOFF
                            : restored.phase() == MySqlHybridEnumeratorState.Phase.SNAPSHOT
                                    ? TableMetrics.Phase.SNAPSHOT
                                    : TableMetrics.Phase.INITIALIZING,
                    complete);
            Map<TableId, Integer> counts = new LinkedHashMap<>();
            restored.pendingSnapshots().forEach(split -> counts.merge(split.tableId(), 1, Integer::sum));
            restored.assignedSnapshots().values().forEach(split -> counts.merge(split.tableId(), 1, Integer::sum));
            restored.finishedSplitInfos().forEach(info -> {
                counts.merge(info.tableId(), 1, Integer::sum);
                metrics.splitFinished(generation, info.splitId(), info.tableId());
            });
            for (int i = 0; i < tables.size(); i++) {
                if (restored.snapshotPlanned() || i < restored.nextTableIndex()) {
                    metrics.planned(generation, tables.get(i), counts.getOrDefault(tables.get(i), 0));
                }
            }
        }
    }

    @Override
    public void start() {
        if (started || closed) throw new IllegalStateException("MySQL Hybrid enumerator cannot start twice");
        started = true;
        if (restoredHandoff) {
            assigner.resumeRestoredHandoff();
            sendResumeEvents();
        } else if (assigner.snapshotComplete()) enterHandoff();
        else planSnapshotAsync();
    }

    @Override
    public void handleSplitRequest(int reader) {
        if (closed) return;
        if (!started || !context.registeredReaders().contains(reader))
            throw new IllegalStateException("Unregistered Hybrid reader");
        waitingReaders.add(reader);
        assignAvailable();
        planSnapshotAsync();
    }

    @Override
    public void addReader(int reader) {
        if (closed) return;
        if (assigner.phase() == MySqlHybridEnumeratorState.Phase.STREAMING) sendResumeEvent(reader);
        assignAvailable();
    }

    @Override
    public void handleSourceEvent(int reader, SourceEvent event) {
        if (closed) return;
        if (event instanceof MySqlLowWatermarkEvent low) {
            if (reader != 0) throw new IllegalArgumentException("Only the Binlog owner may report bootstrap");
            boolean bootstrap = assigner.phase() == MySqlHybridEnumeratorState.Phase.BOOTSTRAP;
            assigner.captureLow(low.offset());
            if (!bootstrap) return;
            metrics.phase(generation, TableMetrics.Phase.SNAPSHOT, false);
            planSnapshotAsync();
        } else if (event instanceof MySqlSnapshotFinishedEvent finished) {
            assigner.finishSnapshot(finished.finished());
            metrics.splitFinished(
                    generation, finished.splitId(), finished.finished().tableId());
            context.sendEventToSourceReader(reader, new MySqlSnapshotFinishedAckEvent(finished.splitId()));
            if (assigner.snapshotComplete()) enterHandoff();
            assignAvailable();
            planSnapshotAsync();
        } else throw new IllegalArgumentException("Unsupported MySQL Hybrid source event");
    }

    @Override
    public void addSplitsBack(List<MySqlHybridSplit> splits, int reader) {
        if (closed || reader < 0 || reader >= context.currentParallelism())
            throw new IllegalStateException("Invalid split return");
        for (MySqlHybridSplit split : splits) {
            if (split instanceof MySqlSnapshotSplit snapshot) assigner.addBack(snapshot);
            else if (split instanceof MySqlHybridBinlogSplit binlog) {
                if (reader != 0) throw new IllegalArgumentException("Binlog belongs to reader zero");
                assigner.addBack(binlog);
            }
        }
        assignAvailable();
    }

    @Override
    public MySqlHybridEnumeratorState snapshotState(long checkpointId) {
        if (!started || closed) throw new IllegalStateException("Inactive MySQL Hybrid enumerator");
        return assigner.snapshot(checkpointId);
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        boolean handoff = assigner.completeCheckpoint(checkpointId);
        metrics.checkpointCompleted(generation, checkpointId, handoff);
        if (handoff) sendResumeEvents();
    }

    @Override
    public void close() {
        closed = true;
        splitter.cancel();
        metrics.sourceClosed(generation);
        waitingReaders.clear();
    }

    private void planSnapshotAsync() {
        if (!started
                || closed
                || planning
                || assigner.snapshotPlanned()
                || assigner.phase() != MySqlHybridEnumeratorState.Phase.SNAPSHOT
                || assigner.pendingSnapshotCount() >= MAX_PENDING_CHUNKS) return;
        int tableIndex = assigner.nextTableIndex();
        int chunkIndex = assigner.nextChunkIndex();
        Long lower = assigner.nextChunkLower();
        if (tableIndex >= tables.size()) throw new IllegalStateException("Incomplete discovery terminal marker");
        TableId table = tables.get(tableIndex);
        planning = true;
        context.callAsync(
                () -> splitter.planNext(table, fingerprint, tableIndex, chunkIndex, lower), (split, failure) -> {
                    planning = false;
                    if (closed) return;
                    if (failure != null) {
                        metrics.readFailed(generation, table, false);
                        throw new IllegalStateException("MySQL snapshot chunk discovery failed", failure);
                    }
                    boolean tableComplete = split.upperExclusive() == null;
                    int nextTable = tableComplete ? tableIndex + 1 : tableIndex;
                    assigner.appendPlan(
                            split,
                            nextTable,
                            tableComplete ? 0 : chunkIndex + 1,
                            split.upperExclusive(),
                            nextTable == tables.size());
                    // Until discovery ends for a table, its denominator remains unknown rather than reporting false
                    // completion.
                    if (tableComplete) metrics.planned(generation, table, chunkIndex + 1);
                    assignAvailable();
                    planSnapshotAsync();
                });
    }

    private void enterHandoff() {
        var high = assigner.finishedSplits().stream()
                .map(FinishedSnapshotSplitInfo::highWatermark)
                .max(BinlogOffsetOrder::compare)
                .orElseThrow();
        assigner.captureHigh(high);
        metrics.phase(generation, TableMetrics.Phase.HANDOFF, false);
    }

    private void assignAvailable() {
        if (closed || !started) return;
        MySqlHybridBinlogSplit binlog = assigner.nextBinlog(0);
        if (binlog != null) {
            if (context.registeredReaders().contains(0)) context.assignSplit(binlog, 0);
            else assigner.addBack(binlog);
            waitingReaders.remove(0);
        }
        if (assigner.phase() != MySqlHybridEnumeratorState.Phase.SNAPSHOT) return;
        for (int reader : List.copyOf(waitingReaders)) {
            if (!context.registeredReaders().contains(reader)) {
                waitingReaders.remove(reader);
                continue;
            }
            MySqlSnapshotSplit split = assigner.nextSnapshot();
            if (split == null) break;
            context.assignSplit(split, reader);
            waitingReaders.remove(reader);
        }
    }

    private void sendResumeEvents() {
        context.registeredReaders().forEach(this::sendResumeEvent);
    }

    private void sendResumeEvent(int reader) {
        context.sendEventToSourceReader(
                reader,
                reader == 0
                        ? new MySqlResumeBinlogEvent(assigner.high(), assigner.finishedSplits())
                        : new MySqlStopSnapshotEvent());
    }
}
