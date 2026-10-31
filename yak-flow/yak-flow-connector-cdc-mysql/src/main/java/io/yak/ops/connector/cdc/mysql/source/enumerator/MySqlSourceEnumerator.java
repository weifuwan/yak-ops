package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.AssignerStatus;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlBinlogSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.BinlogPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaAssembledEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitUpdateAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitUpdateRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsReportEvent;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.LatestFinishedSplitsNumberEvent;
import io.yak.ops.connector.cdc.mysql.source.events.LatestFinishedSplitsNumberRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlStopSnapshotEvent;
import io.yak.ops.connector.cdc.mysql.source.events.TableDiscoveryAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.TableDiscoveryEvent;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Coordinates bounded progressive chunk discovery, durable completion metadata, and the Binlog handoff. */
public final class MySqlSourceEnumerator implements SplitEnumerator<MySqlSplit, PendingSplitsState> {
    private static final int MAX_PENDING_CHUNKS = 32;
    private final SplitEnumeratorContext<MySqlSplit> context;
    private final MySqlChunkSplitter splitter;
    private final MySqlHybridSplitAssigner assigner;
    private final MySqlBinlogSplitAssigner streamAssigner;
    private final boolean initial;
    private final boolean snapshotOnly;
    private boolean snapshotOnlyFinished;
    private final int splitMetaGroupSize;
    private final List<TableId> tables;
    private final MySqlSourceConfig sourceConfig;
    private TableDiscoveryEvent pendingDiscovery;
    private final Set<Integer> discoveryAcknowledgments = new LinkedHashSet<>();
    private final Set<Integer> waitingReaders = new LinkedHashSet<>();
    private final TableMetrics metrics;
    private final long generation;
    private final boolean restoredHandoff;
    private long pendingBinlogUpdateGeneration = -1;
    private boolean binlogReaderSuspended;
    private boolean pendingMetadataGroupSent;
    private boolean started;
    private boolean closed;

    public MySqlSourceEnumerator(
            StartupOptions startupOptions,
            SplitEnumeratorContext<MySqlSplit> context,
            MySqlSourceConfig config,
            int chunkSize,
            PendingSplitsState restored) {
        this(startupOptions, context, config, chunkSize, restored, new TableMetrics());
    }

    public MySqlSourceEnumerator(
            StartupOptions startupOptions,
            SplitEnumeratorContext<MySqlSplit> context,
            MySqlSourceConfig config,
            int chunkSize,
            PendingSplitsState restored,
            TableMetrics metrics) {
        this(
                startupOptions,
                context,
                config,
                chunkSize,
                restored,
                metrics,
                startupOptions.isStreamOnly() ? null : new MySqlChunkSplitter(config, chunkSize));
    }

    /** Sets up the mode-specific assigner behind the single Source coordinator. */
    MySqlSourceEnumerator(
            StartupOptions startupOptions,
            SplitEnumeratorContext<MySqlSplit> context,
            MySqlSourceConfig config,
            int chunkSize,
            PendingSplitsState restored,
            TableMetrics metrics,
            MySqlChunkSplitter planner) {
        this.context = Objects.requireNonNull(context, "context");
        Objects.requireNonNull(startupOptions, "startupOptions");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        sourceConfig = config;
        tables = new ArrayList<>(config.schemas().keySet());
        this.initial = !startupOptions.isStreamOnly();
        this.snapshotOnly = startupOptions.isSnapshotOnly();
        if (initial && config.isReleaseSnapshotMetadataEnabled() && config.isScanNewlyAddedTableEnabled()) {
            throw new IllegalArgumentException(
                    "Snapshot metadata release cannot be enabled with newly-added-table discovery");
        }
        splitMetaGroupSize = config.getSplitMetaGroupSize();
        if (splitMetaGroupSize <= 0) throw new IllegalArgumentException("Invalid Binlog split metadata group size");
        if (initial && restored != null && !(restored instanceof HybridPendingSplitsState)) {
            throw new IllegalArgumentException("INITIAL cannot restore Binlog-only checkpoint state");
        }
        if (!initial && restored != null && !(restored instanceof BinlogPendingSplitsState)) {
            throw new IllegalArgumentException("Stream-only cannot restore snapshot checkpoint state");
        }
        HybridPendingSplitsState restoredHybrid = initial ? (HybridPendingSplitsState) restored : null;
        if (restoredHybrid != null && restoredHybrid.getSnapshotPendingSplits().nextChunkRange() != null) {
            ChunkRange cursor = restoredHybrid.getSnapshotPendingSplits().nextChunkRange();
            TableSchema schema = config.schemas().get(cursor.tableId());
            // A removed table is reconciled by the Snapshot Assigner below. For a still-captured
            // table, fail closed if the checkpointed typed cursor no longer matches its key.
            if (schema != null) {
                Column selected = ChunkUtils.requireSupportedSnapshot(
                        schema, config.chunkKeyColumns().get(cursor.tableId()));
                if (!cursor.column().equals(selected.name())
                        || cursor.type() != selected.dataType().getTypeRoot()) {
                    throw new IllegalArgumentException("Restored typed chunk cursor differs from the selected table");
                }
            }
        }
        tables.forEach(metrics::register);
        generation = metrics.beginSnapshot(restored != null);
        splitter = planner;
        assigner = initial ? new MySqlHybridSplitAssigner(startupOptions, restoredHybrid, splitMetaGroupSize) : null;
        if (initial) {
            assigner.initializeTables(
                    tables, config.isScanNewlyAddedTableEnabled(), config.isReleaseSnapshotMetadataEnabled());
        }
        streamAssigner =
                initial ? null : new MySqlBinlogSplitAssigner(startupOptions, (BinlogPendingSplitsState) restored);
        restoredHandoff = restoredHybrid != null && restoredHybrid.phase() == HybridPendingSplitsState.Phase.HANDOFF;
        if (context.currentParallelism() < 1 || context.currentParallelism() > (initial ? 16 : 1)) {
            throw new IllegalArgumentException("Invalid MySQL Hybrid parallelism or discovery position");
        }
        if (restoredHybrid != null) {
            boolean complete = restoredHandoff || restoredHybrid.phase() == HybridPendingSplitsState.Phase.STREAMING;
            metrics.phase(
                    generation,
                    complete
                            ? TableMetrics.Phase.HANDOFF
                            : restoredHybrid.phase() == HybridPendingSplitsState.Phase.SNAPSHOT
                                    ? TableMetrics.Phase.SNAPSHOT
                                    : TableMetrics.Phase.INITIALIZING,
                    complete);
            Map<TableId, Integer> counts = new LinkedHashMap<>();
            restoredHybrid
                    .getSnapshotPendingSplits()
                    .getRemainingSplits()
                    .forEach(split -> counts.merge(split.tableId(), 1, Integer::sum));
            restoredHybrid
                    .getSnapshotPendingSplits()
                    .getAssignedSplits()
                    .values()
                    .forEach(split -> counts.merge(split.tableId(), 1, Integer::sum));
            restoredHybrid.getSnapshotPendingSplits().getFinishedSplitInfos().forEach(info -> {
                counts.merge(info.tableId(), 1, Integer::sum);
                metrics.splitFinished(generation, info.splitId(), info.tableId());
            });
            for (int i = 0; i < tables.size(); i++) {
                if (restoredHybrid.getSnapshotPendingSplits().snapshotPlanned()
                        || i < restoredHybrid.getSnapshotPendingSplits().nextTableIndex()) {
                    metrics.planned(generation, tables.get(i), counts.getOrDefault(tables.get(i), 0));
                }
            }
        }
    }

    @Override
    public void start() {
        if (started || closed) throw new IllegalStateException("MySQL Hybrid enumerator cannot start twice");
        started = true;
        if (initial) assigner.open();
        else streamAssigner.open();
        if (!initial) return;
        if (sourceConfig.isScanNewlyAddedTableEnabled()) {
            context.callAsync(this::discoverCandidates, this::admitCandidate, 1000L, 5000L);
        }
        if (snapshotOnly && assigner.snapshotComplete()) {
            finishSnapshotOnly();
        } else if (assigner.phase() == HybridPendingSplitsState.Phase.STREAMING) {
            if (isNewlyAddedProtocolStatus()) {
                requestBinlogSplitUpdateIfNeed(
                        assigner.isNewlyAddedAssignmentFinished() && pendingBinlogUpdateGeneration < 0);
            }
            assignAvailable();
        } else if (assigner.snapshotComplete()) {
            if (assigner.phase() == HybridPendingSplitsState.Phase.SNAPSHOT) enterHandoff();
            assignAvailable();
        } else {
            assignAvailable();
            planSnapshotAsync();
        }
    }

    @Override
    public void handleSplitRequest(int reader, String requesterHostname) {
        if (closed) return;
        if (!started || !context.registeredReaders().containsKey(reader))
            throw new IllegalStateException("Unregistered Hybrid reader");
        if (snapshotOnly && assigner.snapshotComplete()) {
            if (!snapshotOnlyFinished) finishSnapshotOnly();
            return;
        }
        if (!initial) {
            if (reader != 0) throw new IllegalArgumentException("Binlog-only source requires reader zero");
            MySqlBinlogSplit next = (MySqlBinlogSplit) streamAssigner.getNext().orElse(null);
            if (next != null) context.assignSplit(next, 0);
            return;
        }
        if (assigner.phase() == HybridPendingSplitsState.Phase.STREAMING && !assigner.isAssigningNewlyAddedTables()) {
            // Reader 0 may request a returned Binlog split after a region restart.
            if (reader == 0) assignAvailable();
            return;
        }
        waitingReaders.add(reader);
        assignAvailable();
        planSnapshotAsync();
    }

    @Override
    public void addReader(int reader) {
        if (closed || !initial) return;
        if (pendingDiscovery != null) {
            discoveryAcknowledgments.remove(reader);
            context.sendEventToSourceReader(reader, pendingDiscovery);
        }
        if (snapshotOnly && assigner.snapshotComplete()) {
            if (snapshotOnlyFinished) stopSnapshotOnlyReader(reader);
            else finishSnapshotOnly();
            return;
        }
        if (assigner.phase() == HybridPendingSplitsState.Phase.STREAMING && isNewlyAddedProtocolStatus()) {
            if (reader == 0) {
                if (pendingBinlogUpdateGeneration >= 0) {
                    // A reader attempt can restart while the coordinator remains alive.
                    // Resend the current generation, not a new one on every addReader callback.
                    binlogReaderSuspended = false;
                    pendingMetadataGroupSent = false;
                    context.sendEventToSourceReader(
                            0, new BinlogSplitUpdateRequestEvent(pendingBinlogUpdateGeneration));
                } else {
                    requestBinlogSplitUpdateIfNeed(assigner.isNewlyAddedAssignmentFinished());
                }
            } else if (assigner.isAssigningNewlyAddedTables()) {
                context.sendEventToSourceReader(reader, new FinishedSnapshotSplitsRequestEvent());
            }
            return;
        }
        if (assigner.phase() == HybridPendingSplitsState.Phase.STREAMING) {
            // A restored reader owns its checkpointed Binlog split. No initial Resume event.
            if (reader == 0 && !assigner.isSnapshotMetaReleased()) {
                assigner.onBinlogReaderReset();
            }
        } else if (assigner.phase() == HybridPendingSplitsState.Phase.SNAPSHOT) {
            context.sendEventToSourceReader(reader, new FinishedSnapshotSplitsRequestEvent());
        }
        assignAvailable();
    }

    @Override
    public void handleSourceEvent(int reader, SourceEvent event) {
        if (closed) return;
        if (!initial) throw new IllegalArgumentException("Binlog-only coordinator does not receive snapshot events");
        if (event instanceof TableDiscoveryAckEvent ack) {
            if (pendingDiscovery == null || !pendingDiscovery.table().equals(ack.table())) return;
            if (!context.registeredReaders().containsKey(reader))
                throw new IllegalArgumentException("Unregistered discovery acknowledgment");
            discoveryAcknowledgments.add(reader);
            if (discoveryAcknowledgments.size() == context.currentParallelism()) {
                TableDiscoveryEvent discovery = pendingDiscovery;
                pendingDiscovery = null;
                discoveryAcknowledgments.clear();
                splitter.registerTable(discovery.table(), discovery.schema());
                tables.add(discovery.table());
                metrics.register(discovery.table());
                assigner.addDiscoveredTable(discovery.table());
                waitingReaders.addAll(context.registeredReaders().keySet());
                requestBinlogSplitUpdateIfNeed(false);
            }
        } else if (event instanceof LatestFinishedSplitsNumberRequestEvent request) {
            if (reader != 0) {
                throw new IllegalArgumentException("Only the Binlog owner may request finished Snapshot count");
            }
            if (assigner.phase() != HybridPendingSplitsState.Phase.STREAMING || assigner.isSnapshotMetaReleased()) {
                return;
            }
            if (isNewlyAddedProtocolStatus() && pendingBinlogUpdateGeneration < 0) {
                // Region failover can restore an older PAUSED reader against an already
                // completed Snapshot Assigner. Restart the handoff using the current state.
                requestBinlogSplitUpdateIfNeed(assigner.isNewlyAddedAssignmentFinished());
            }
            int latest = assigner.finishedSplits().size();
            // The reader may have more old fences after captured tables were removed.
            // The authoritative completed metadata is sent in the following group exchange.
            long updateGeneration = pendingBinlogUpdateGeneration >= 0
                    ? pendingBinlogUpdateGeneration
                    : assigner.getBinlogAssignmentGeneration();
            context.sendEventToSourceReader(
                    0,
                    new LatestFinishedSplitsNumberEvent(
                            latest, updateGeneration, !assigner.isAssigningNewlyAddedTables()));
        } else if (event instanceof BinlogSplitMetaRequestEvent request) {
            if (reader != 0) throw new IllegalArgumentException("Only the Binlog owner may request metadata");
            AssignerStatus status = assigner.getAssignerStatus();
            if (assigner.phase() == HybridPendingSplitsState.Phase.STREAMING
                    && (status == AssignerStatus.NEWLY_ADDED_ASSIGNING
                            || status == AssignerStatus.NEWLY_ADDED_ASSIGNING_SNAPSHOT_FINISHED
                            || status == AssignerStatus.NEWLY_ADDED_ASSIGNING_FINISHED)) {
                if (request.getGeneration() != assigner.getBinlogAssignmentGeneration()) return;
                binlogReaderSuspended = true;
                if (status == AssignerStatus.NEWLY_ADDED_ASSIGNING) {
                    assignAvailable();
                    planSnapshotAsync();
                } else {
                    pendingMetadataGroupSent = true;
                    sendBinlogMetadataGroup(request.getGroupId(), request.getGeneration());
                }
            } else {
                // An INITIAL reader can restore from a checkpoint after its former
                // coordinator generation has changed. Re-advertise the current
                // generation using group zero; never let a stale ACK release metadata.
                long current = assigner.getBinlogAssignmentGeneration();
                if (request.getGeneration() != current) {
                    sendBinlogMetadataGroup(0, current);
                } else {
                    sendBinlogMetadataGroup(request.getGroupId(), current);
                }
            }
        } else if (event instanceof BinlogSplitMetaAssembledEvent assembled) {
            if (assigner.isSnapshotMetaReleased()) return;
            if (reader != 0) throw new IllegalArgumentException("Only the Binlog owner may ACK metadata");
            if (!assigner.onBinlogSplitMetaAssembled(assembled.getBinlogAssignmentGeneration())) return;
            // The INITIAL Binlog split already runs from its own checkpointed offset.
            // Only a genuinely suspended newly-added-table reader needs a Resume event.
            if (pendingBinlogUpdateGeneration >= 0
                    && (assigner.isNewlyAddedSnapshotFinished() || assigner.isNewlyAddedAssignmentFinished())) {
                sendResumeEvents();
            }
        } else if (event instanceof BinlogSplitUpdateAckEvent ack) {
            if (reader != 0) throw new IllegalArgumentException("Only the Binlog owner may ACK a split update");
            if (pendingBinlogUpdateGeneration < 0 || ack.getGeneration() != pendingBinlogUpdateGeneration) return;
            assigner.onBinlogSplitUpdated();
            pendingBinlogUpdateGeneration = -1;
            binlogReaderSuspended = false;
            pendingMetadataGroupSent = false;
            waitingReaders.clear();
            assignAvailable();
            planSnapshotAsync();
        } else if (event instanceof FinishedSnapshotSplitsReportEvent finished) {
            assigner.onFinishedSplits(finished.getFinishedOffsets());
            assigner.captureSnapshotSchemaHistory(finished.getFinishedOffsets(), finished.getFinishedSchemaHistories());
            List<String> acknowledged = new ArrayList<>();
            Map<String, FinishedSnapshotSplitInfo> completed = new LinkedHashMap<>();
            assigner.finishedSplits().forEach(info -> completed.put(info.splitId(), info));
            for (String splitId : finished.getFinishedOffsets().keySet()) {
                FinishedSnapshotSplitInfo info = Objects.requireNonNull(
                        completed.get(splitId), "Completed snapshot metadata must be available after reporting");
                metrics.splitFinished(generation, info.splitId(), info.tableId());
                acknowledged.add(info.splitId());
            }
            if (!acknowledged.isEmpty()) {
                context.sendEventToSourceReader(reader, new FinishedSnapshotSplitsAckEvent(acknowledged));
            }
            if (assigner.snapshotComplete()) {
                if (snapshotOnly) finishSnapshotOnly();
                else enterHandoff();
            }
            assignAvailable();
            planSnapshotAsync();
        } else throw new IllegalArgumentException("Unsupported MySQL Hybrid source event");
    }

    private Map<TableId, io.yak.ops.core.schema.Schema> discoverCandidates() throws Exception {
        MySqlSourceConfig copy = sourceConfig.discoveryCopy();
        MySqlChunkSplitter discovery = new MySqlChunkSplitter(copy, copy.getSplitSize());
        discovery.discoverTables();
        return copy.physicalSchemas();
    }

    private void admitCandidate(Map<TableId, io.yak.ops.core.schema.Schema> discovered, Throwable failure) {
        if (closed) return;
        if (failure != null) throw new IllegalStateException("MySQL table discovery failed", failure);
        if (pendingDiscovery != null
                || assigner.phase() != HybridPendingSplitsState.Phase.STREAMING
                || !AssignerStatus.isAssigningFinished(assigner.getAssignerStatus())
                || context.registeredReaders().size() != context.currentParallelism()) return;
        for (Map.Entry<TableId, io.yak.ops.core.schema.Schema> entry : discovered.entrySet()) {
            if (tables.contains(entry.getKey())) continue;
            if (tables.size() >= 100)
                throw new IllegalArgumentException("MySQL dynamic discovery exceeds the 100-table limit");
            pendingDiscovery = new TableDiscoveryEvent(entry.getKey(), entry.getValue());
            discoveryAcknowledgments.clear();
            for (int reader : context.registeredReaders().keySet())
                context.sendEventToSourceReader(reader, pendingDiscovery);
            return;
        }
    }

    @Override
    public void addSplitsBack(List<MySqlSplit> splits, int reader) {
        if (closed || reader < 0 || reader >= context.currentParallelism())
            throw new IllegalStateException("Invalid split return");
        if (initial) {
            for (MySqlSplit split : splits) {
                if (split instanceof MySqlBinlogSplit && reader != 0) {
                    throw new IllegalArgumentException("Binlog belongs to reader zero");
                }
            }
            assigner.addSplits(splits);
            assignAvailable();
        } else {
            if (reader != 0) throw new IllegalArgumentException("Binlog belongs to reader zero");
            streamAssigner.addSplits(splits);
        }
    }

    @Override
    public PendingSplitsState snapshotState(long checkpointId) {
        if (!started || closed) throw new IllegalStateException("Inactive MySQL Hybrid enumerator");
        return initial ? assigner.snapshotState(checkpointId) : streamAssigner.snapshotState(checkpointId);
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        boolean waitingForHandoff = initial && assigner.phase() == HybridPendingSplitsState.Phase.HANDOFF;
        if (initial) assigner.notifyCheckpointComplete(checkpointId);
        else streamAssigner.notifyCheckpointComplete(checkpointId);
        if (initial) {
            requestBinlogSplitUpdateIfNeed(false);
            assignAvailable();
            planSnapshotAsync();
        }
        boolean handoff = waitingForHandoff && assigner.phase() == HybridPendingSplitsState.Phase.STREAMING;
        metrics.checkpointCompleted(generation, checkpointId, handoff);
    }

    @Override
    public void close() {
        closed = true;
        if (splitter != null) splitter.cancel();
        if (assigner != null) assigner.close();
        if (streamAssigner != null) streamAssigner.close();
        metrics.sourceClosed(generation);
        waitingReaders.clear();
    }

    private void planSnapshotAsync() {
        if (closed || !started || !initial) return;
        boolean initialSnapshot = assigner.phase() == HybridPendingSplitsState.Phase.SNAPSHOT;
        boolean newlyAddedSnapshot = assigner.phase() == HybridPendingSplitsState.Phase.STREAMING
                && assigner.isAssigningNewlyAddedTables()
                && binlogReaderSuspended;
        if (!initialSnapshot && !newlyAddedSnapshot) return;
        assigner.discoverNextAsync(
                tables,
                splitter,
                context,
                MAX_PENDING_CHUNKS,
                (split, tableChunkCount) -> {
                    if (closed) return;
                    if (split.chunkRange().last()) metrics.planned(generation, split.tableId(), tableChunkCount);
                    assignAvailable();
                    planSnapshotAsync();
                },
                (table, failure) -> {
                    metrics.readFailed(generation, table, false);
                    throw new IllegalStateException("MySQL snapshot chunk discovery failed", failure);
                });
    }

    private void finishSnapshotOnly() {
        if (snapshotOnlyFinished) return;
        snapshotOnlyFinished = true;
        metrics.phase(generation, TableMetrics.Phase.HANDOFF, false);
        for (int reader : context.registeredReaders().keySet()) {
            stopSnapshotOnlyReader(reader);
        }
    }

    private void stopSnapshotOnlyReader(int reader) {
        context.sendEventToSourceReader(reader, new MySqlStopSnapshotEvent());
        context.signalNoMoreSplits(reader);
    }

    private void enterHandoff() {
        DebeziumOffset high = assigner.finishedSplits().stream()
                .map(FinishedSnapshotSplitInfo::highWatermark)
                .max(DebeziumOffset::compareTo)
                .orElseThrow();
        assigner.captureHigh(high);
        metrics.phase(generation, TableMetrics.Phase.HANDOFF, false);
        // The Binlog split is created and assigned only after the Snapshot
        // Assigner has received a completed checkpoint.
    }

    private void assignAvailable() {
        if (closed || !started) return;
        if (context.registeredReaders().containsKey(0)) {
            MySqlBinlogSplit binlog = assigner.nextBinlog();
            if (binlog != null) {
                context.assignSplit(binlog, 0);
                waitingReaders.remove(0);
            }
        }
        boolean initialSnapshot = assigner.phase() == HybridPendingSplitsState.Phase.SNAPSHOT;
        boolean newlyAddedSnapshot = assigner.phase() == HybridPendingSplitsState.Phase.STREAMING
                && assigner.isAssigningNewlyAddedTables()
                && binlogReaderSuspended;
        if (!initialSnapshot && !newlyAddedSnapshot) return;
        for (int reader : List.copyOf(waitingReaders)) {
            if (!context.registeredReaders().containsKey(reader)) {
                waitingReaders.remove(reader);
                continue;
            }
            // Snapshot readers may include subtask IDs other than zero. Keep the Binlog
            // split reserved for reader zero even if that reader is temporarily unavailable.
            MySqlSplit split = assigner.nextSnapshot();
            if (split == null) break;
            context.assignSplit(split, reader);
            waitingReaders.remove(reader);
        }
    }

    /**
     * Serve a bounded Flink-style metadata group. Reader 0 requests subsequent groups
     * and ACKs only once its complete set of Snapshot fences has been assembled.
     */
    private void sendBinlogMetadataGroup(int group, long assignmentGeneration) {
        if (assigner.isSnapshotMetaReleased()) return;
        if (closed
                || !context.registeredReaders().containsKey(0)
                || assignmentGeneration != assigner.getBinlogAssignmentGeneration()
                || (assigner.phase() != HybridPendingSplitsState.Phase.HANDOFF
                        && assigner.phase() != HybridPendingSplitsState.Phase.STREAMING)) {
            return;
        }
        List<FinishedSnapshotSplitInfo> infos = assigner.finishedSplits();
        if (infos.isEmpty()) throw new IllegalStateException("Hybrid handoff has no completed Chunk metadata");
        int groups = (int) (((long) infos.size() + splitMetaGroupSize - 1) / splitMetaGroupSize);
        if (group < 0 || group >= groups) throw new IllegalArgumentException("Invalid Binlog metadata group request");
        int start = group * splitMetaGroupSize;
        int end = Math.min(start + splitMetaGroupSize, infos.size());
        List<byte[]> frames = infos.subList(start, end).stream()
                .map(FinishedSnapshotSplitInfo::serialize)
                .toList();
        context.sendEventToSourceReader(
                0, new BinlogSplitMetaEvent(group, groups, infos.size(), assignmentGeneration, frames));
    }

    private void sendResumeEvents() {
        waitingReaders.clear();
        context.registeredReaders().keySet().forEach(this::sendResumeEvent);
    }

    private boolean isNewlyAddedProtocolStatus() {
        AssignerStatus status = assigner.getAssignerStatus();
        return status == AssignerStatus.NEWLY_ADDED_ASSIGNING
                || status == AssignerStatus.NEWLY_ADDED_ASSIGNING_SNAPSHOT_FINISHED
                || status == AssignerStatus.NEWLY_ADDED_ASSIGNING_FINISHED;
    }

    /** Pauses Binlog before assigning new-table Snapshot chunks; after completion it requests updated fences. */
    private void requestBinlogSplitUpdateIfNeed(boolean force) {
        if (closed
                || !initial
                || assigner.phase() != HybridPendingSplitsState.Phase.STREAMING
                || assigner.isSnapshotMetaReleased()
                || !isNewlyAddedProtocolStatus()
                || !context.registeredReaders().containsKey(0)) return;
        if (pendingBinlogUpdateGeneration >= 0 && !force) {
            if (binlogReaderSuspended && !assigner.isAssigningNewlyAddedTables() && !pendingMetadataGroupSent) {
                pendingMetadataGroupSent = true;
                sendBinlogMetadataGroup(0, pendingBinlogUpdateGeneration);
            }
            return;
        }
        if (!force && pendingBinlogUpdateGeneration < 0 && assigner.isNewlyAddedAssignmentFinished()) return;
        assigner.onBinlogReaderReset();
        pendingBinlogUpdateGeneration = assigner.getBinlogAssignmentGeneration();
        binlogReaderSuspended = false;
        pendingMetadataGroupSent = false;
        context.sendEventToSourceReader(0, new BinlogSplitUpdateRequestEvent(pendingBinlogUpdateGeneration));
    }

    private void sendResumeEvent(int reader) {
        context.sendEventToSourceReader(
                reader, reader == 0 ? new MySqlResumeBinlogEvent(assigner.high()) : new MySqlStopSnapshotEvent());
    }
}
