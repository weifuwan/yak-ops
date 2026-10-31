package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.source.reader.SingleThreadMultiplexSourceReaderBase;
import io.yak.ops.connector.cdc.base.source.reader.SchemaChangeHandler;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
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
import io.yak.ops.connector.cdc.mysql.source.events.MySqlLowWatermarkEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.events.TableDiscoveryAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.TableDiscoveryEvent;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlFetchedRecord;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitState;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.data.TableRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Mailbox-owned SourceReader for parallel snapshot ranges and one deferred Binlog stream.
 *
 * <p>Finished but not acknowledged snapshot splits remain in Reader checkpoint state until
 * the Enumerator confirms receipt of their completion. The Binlog gate opens only through
 * a coordinator event after the snapshot handoff checkpoint completes.
 */
public final class MySqlSourceReader
        extends SingleThreadMultiplexSourceReaderBase<MySqlFetchedRecord, TableRecord, MySqlSplit, MySqlSplitState> {

    private final MySqlSplitReader splits;
    private final MySqlSourceReaderContext readerContext;
    private final SchemaChangeHandler schemaChanges;
    private final TableMetrics metrics;
    private final long generation;
    private final boolean initial;
    private final boolean snapshotOnly;
    private final Map<String, MySqlSnapshotSplit> finishedUnacked = new LinkedHashMap<>();
    private MySqlResumeBinlogEvent resumeEvent;
    private MySqlBinlogSplit suspendedBinlogSplit;
    private MySqlBinlogSplit uncompletedBinlogSplit;
    private long pendingBinlogUpdateGeneration = -1;
    private final BinlogSplitMetaGroupAssembler binlogMetadata = new BinlogSplitMetaGroupAssembler();
    private final java.util.Set<String> acknowledged = new java.util.HashSet<>();
    private final Map<String, Long> finishedCheckpoint = new LinkedHashMap<>();
    private TableDiscoveryEvent discoveredTable;

    public MySqlSourceReader(
            MySqlSourceConfig config, SourceReaderContext context, TableMetrics metrics, boolean initial) {
        this(config, new MySqlSourceReaderContext(context), metrics, initial);
    }

    private MySqlSourceReader(
            MySqlSourceConfig config, MySqlSourceReaderContext readerContext, TableMetrics metrics, boolean initial) {
        this(
                new MySqlSplitReader(
                        config, metrics, readerContext.getSourceReaderContext().getIndexOfSubtask(), readerContext),
                readerContext,
                metrics,
                initial,
                config.getStartupOptions().isSnapshotOnly(),
                sourceId(config, readerContext.getSourceReaderContext()));
    }

    private MySqlSourceReader(
            MySqlSplitReader splits,
            MySqlSourceReaderContext readerContext,
            TableMetrics metrics,
            boolean initial,
            boolean snapshotOnly,
            String sourceId) {
        super(
                () -> splits,
                new MySqlRecordEmitter(readerContext, splits, metrics, sourceId),
                readerContext.getSourceReaderContext());
        this.readerContext = readerContext;
        this.schemaChanges = readerContext.schemaChanges();
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.generation = metrics.generation();
        this.splits = splits;
        this.initial = initial;
        this.snapshotOnly = snapshotOnly;
    }

    /**
     * Bind event identity to a stable task execution, never to mutable split checkpoints.
     *
     * <p>The instance ID remains constant across full-job checkpoint restoration.
     * Direct connector callers without an execution ID use Debezium's logical source name.
     */
    private static String sourceId(MySqlSourceConfig config, SourceReaderContext context) {
        String executionId = context.getConfiguration().get(ExecutionOptions.LOG_ID);
        return executionId.isBlank() ? config.topicPrefix() : executionId + ":" + config.topicPrefix();
    }

    @Override
    public final InputStatus pollNext(ReaderOutput<TableRecord> output) throws Exception {
        if (!schemaChanges.tryCompleteSchemaChange()) return InputStatus.NOTHING_AVAILABLE;
        if (discoveredTable != null) {
            TableDiscoveryEvent discovery = discoveredTable;
            discoveredTable = null;
            schemaChanges.submitSchemaChange(
                    new io.yak.ops.core.event.SourceSchemaChange(
                            discovery.eventId(), discovery.table(), null, discovery.schema()),
                    () -> {
                        splits.registerDiscoveredTable(discovery.table(), discovery.schema());
                        context.sendSourceEventToCoordinator(new TableDiscoveryAckEvent(discovery.table()));
                    });
            return InputStatus.NOTHING_AVAILABLE;
        }
        return super.pollNext(output);
    }

    @Override
    public final CompletableFuture<Void> isAvailable() {
        CompletableFuture<Void> pending = schemaChanges.getAvailableFuture();
        if (pending == null && discoveredTable != null) return CompletableFuture.completedFuture(null);
        return pending == null ? super.isAvailable() : pending;
    }

    @Override
    public final void start() {
        context.sendSplitRequest();
    }

    /**
     * Re-report a checkpointed initial Binlog anchor after reader recovery.
     *
     * <p>Flink CDC re-reports coordinator messages from restored Reader state. The
     * BOOTSTRAP -> SNAPSHOT transition must not depend on a heartbeat that was emitted
     * before the last durable checkpoint. Streaming/suspended update splits are excluded.
     */
    @Override
    public void addSplits(List<MySqlSplit> added) throws Exception {
        if (!initial) {
            super.addSplits(added);
            return;
        }
        List<MySqlSplit> active = new ArrayList<>();
        for (MySqlSplit split : added) {
            if (split instanceof MySqlSnapshotSplit snapshot && snapshot.finished() != null) {
                // Flink CDC's finished-unacked state must be re-reported, never
                // resubmitted to JDBC snapshot execution after a Reader recovery.
                finishedUnacked.put(snapshot.splitId(), snapshot);
            } else if (split instanceof MySqlBinlogSplit binlog
                    && binlog.phase() == MySqlBinlogSplit.Phase.PAUSED
                    && !binlog.finishedSplits().isEmpty()) {
                // Flink CDC keeps a restored suspended Binlog split outside the active fetcher:
                // there is no running Binlog task to finish, and replaying this split through
                // SourceReaderBase would make the resumed split a duplicate split ID.
                if (suspendedBinlogSplit != null) {
                    throw new IllegalStateException("Only one suspended MySQL Binlog split may be restored");
                }
                suspendedBinlogSplit = binlog;
                readerContext.suspendBinlogSplitReader();
                context.sendSourceEventToCoordinator(new LatestFinishedSplitsNumberRequestEvent(
                        binlog.finishedSplits().size()));
                context.sendSplitRequest();
            } else if (split instanceof MySqlBinlogSplit binlog
                    && binlog.phase() == MySqlBinlogSplit.Phase.STREAMING
                    && !binlog.isCompletedSplit()) {
                // Flink CDC: keep incomplete initial Binlog outside the fetcher until
                // all finished Snapshot groups have been assembled.
                if (uncompletedBinlogSplit != null) {
                    throw new IllegalStateException("Only one incomplete MySQL Binlog split may be restored");
                }
                uncompletedBinlogSplit = binlog;
                context.sendSourceEventToCoordinator(new BinlogSplitMetaRequestEvent(0, 0));
            } else {
                active.add(split);
                if (split instanceof MySqlBinlogSplit binlog
                        && binlog.phase() == MySqlBinlogSplit.Phase.PAUSED
                        && binlog.finishedSplits().isEmpty()) {
                    context.sendSourceEventToCoordinator(new MySqlLowWatermarkEvent(binlog.offset()));
                    context.sendSplitRequest();
                }
            }
        }
        super.addSplits(active);
        for (MySqlSplit split : active) {
            if (split instanceof MySqlBinlogSplit binlog
                    && binlog.phase() == MySqlBinlogSplit.Phase.STREAMING
                    && !binlog.finishedSplits().isEmpty()) {
                // Deferred INITIAL delivers a complete Binlog split directly.
                context.sendSourceEventToCoordinator(new BinlogSplitMetaAssembledEvent(0));
                // If the coordinator restarted, discover its current assignment
                // generation before allowing Snapshot metadata release.
                context.sendSourceEventToCoordinator(new BinlogSplitMetaRequestEvent(0, 0));
                metrics.streaming(generation);
            }
        }
        reportFinishedSplits();
    }

    @Override
    protected MySqlSplitState initializedState(MySqlSplit split) {
        MySqlSplitState state = MySqlSplitState.fromSplit(split);
        if (resumeEvent != null
                && split instanceof MySqlBinlogSplit binlog
                && binlog.phase() != MySqlBinlogSplit.Phase.STANDALONE) {
            state.resume(resumeEvent.highWatermark(), binlogMetadata.finishedSplits(resumeEvent.highWatermark()));
        }
        return state;
    }

    @Override
    protected MySqlSplit toSplitType(String splitId, MySqlSplitState state) {
        return state.checkpoint(state.binlog() ? splits.snapshotHistory() : null);
    }

    @Override
    protected void onSplitFinished(Map<String, MySqlSplitState> finished) {
        Map<String, DebeziumOffset> finishedOffsets = new LinkedHashMap<>();
        Map<String, byte[]> finishedHistories = new LinkedHashMap<>();
        boolean requestNextSplit = false;
        for (var entry : finished.entrySet()) {
            if (entry.getValue().binlog()) {
                if (snapshotOnly) {
                    // Bounded snapshot bootstrap is a reader-local schema anchor,
                    // not a continuous Binlog split requiring metadata handoff.
                    continue;
                }
                if (pendingBinlogUpdateGeneration < 0 || !readerContext.isBinlogSplitReaderSuspended()) {
                    throw new IllegalStateException(
                            "A continuous Binlog split can finish only for a requested metadata update");
                }
                MySqlBinlogSplit checkpointed =
                        (MySqlBinlogSplit) entry.getValue().checkpoint(splits.snapshotHistory());
                suspendedBinlogSplit = MySqlBinlogSplit.toSuspendedBinlogSplit(checkpointed);
                splits.suspendBinlog(suspendedBinlogSplit);
                context.sendSourceEventToCoordinator(new BinlogSplitMetaRequestEvent(0, pendingBinlogUpdateGeneration));
                requestNextSplit = true;
                continue;
            }
            MySqlSplit split = entry.getValue().checkpoint(null);
            if (!(split instanceof MySqlSnapshotSplit snapshot)) {
                throw new IllegalStateException("Unknown completed MySQL split type");
            }
            finishedUnacked.put(snapshot.splitId(), snapshot);
            finishedOffsets.put(
                    snapshot.splitId(),
                    Objects.requireNonNull(snapshot.finished(), "Finished chunk has no watermark")
                            .highWatermark());
            if (snapshot.finishedSchemaHistory().length != 0) {
                finishedHistories.put(snapshot.splitId(), snapshot.finishedSchemaHistory());
            }
            requestNextSplit = true;
        }
        if (!finishedOffsets.isEmpty()) {
            context.sendSourceEventToCoordinator(
                    new FinishedSnapshotSplitsReportEvent(finishedOffsets, finishedHistories));
        }
        if (requestNextSplit) context.sendSplitRequest();
    }

    private void reportFinishedSplits() {
        Map<String, DebeziumOffset> finishedOffsets = new LinkedHashMap<>();
        Map<String, byte[]> finishedHistories = new LinkedHashMap<>();
        finishedUnacked.forEach((id, split) -> {
            if (split.finished() != null) {
                finishedOffsets.put(id, split.finished().highWatermark());
                if (split.finishedSchemaHistory().length != 0) {
                    finishedHistories.put(id, split.finishedSchemaHistory());
                }
            }
        });
        if (!finishedOffsets.isEmpty()) {
            context.sendSourceEventToCoordinator(
                    new FinishedSnapshotSplitsReportEvent(finishedOffsets, finishedHistories));
        }
    }

    @Override
    public List<MySqlSplit> snapshotState(long checkpointId) {
        schemaChanges.requireNoPendingSchemaChange();
        List<MySqlSplit> active = new ArrayList<>(super.snapshotState(checkpointId));
        forEachActiveSplitState(state -> state.snapshotCheckpoint(checkpointId));
        acknowledged.forEach(id -> finishedCheckpoint.putIfAbsent(id, checkpointId));
        active.addAll(finishedUnacked.values());
        if (suspendedBinlogSplit != null
                && active.stream().noneMatch(split -> MySqlBinlogSplit.ID.equals(split.splitId()))) {
            active.add(suspendedBinlogSplit);
        }
        if (uncompletedBinlogSplit != null
                && active.stream().noneMatch(split -> MySqlBinlogSplit.ID.equals(split.splitId()))) {
            active.add(uncompletedBinlogSplit);
        }
        return List.copyOf(active);
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        if (!initial) metrics.checkpointCompleted(generation, checkpointId, false);
        forEachActiveSplitState(state -> {
            state.completeCheckpoint(checkpointId);
        });
        finishedCheckpoint.entrySet().removeIf(entry -> {
            if (entry.getValue() > checkpointId) return false;
            finishedUnacked.remove(entry.getKey());
            acknowledged.remove(entry.getKey());
            return true;
        });
    }

    @Override
    public void handleSourceEvents(SourceEvent event) {
        if (!initial) throw new IllegalArgumentException("Binlog-only reader does not accept hybrid events");
        if (event instanceof TableDiscoveryEvent discovery) {
            if (discoveredTable != null && !discoveredTable.table().equals(discovery.table())) {
                throw new IllegalStateException("Previous discovered table has not been admitted");
            }
            discoveredTable = discovery;
        } else if (event instanceof BinlogSplitUpdateRequestEvent update) {
            if (update.getGeneration() < pendingBinlogUpdateGeneration) return;
            pendingBinlogUpdateGeneration = update.getGeneration();
            readerContext.suspendBinlogSplitReader();
            if (suspendedBinlogSplit != null) {
                context.sendSourceEventToCoordinator(new BinlogSplitMetaRequestEvent(0, pendingBinlogUpdateGeneration));
                context.sendSplitRequest();
            }
        } else if (event instanceof LatestFinishedSplitsNumberEvent latest) {
            if (suspendedBinlogSplit == null) return;
            if (latest.getLatestFinishedSplitsNumber() == 0) {
                throw new IllegalStateException("Binlog cannot resume without completed Snapshot fences");
            }
            // Flink CDC allows the completed count to shrink when tables are removed.
            // The coordinator's full metadata groups replace the checkpointed fence set.
            if (latest.getBinlogAssignmentGeneration() < pendingBinlogUpdateGeneration) return;
            pendingBinlogUpdateGeneration = latest.getBinlogAssignmentGeneration();
            readerContext.suspendBinlogSplitReader();
            // An unfinished assignment treats the request as a pause acknowledgement and
            // defers metadata delivery until the Snapshot completion checkpoint is durable.
            context.sendSourceEventToCoordinator(new BinlogSplitMetaRequestEvent(0, pendingBinlogUpdateGeneration));
        } else if (event instanceof FinishedSnapshotSplitsAckEvent ack) {
            ack.getFinishedSplits().forEach(id -> {
                if (finishedUnacked.containsKey(id)) acknowledged.add(id);
            });
        } else if (event instanceof FinishedSnapshotSplitsRequestEvent) {
            reportFinishedSplits();
        } else if (event instanceof BinlogSplitMetaEvent group) {
            if (pendingBinlogUpdateGeneration >= 0
                    && group.getBinlogAssignmentGeneration() != pendingBinlogUpdateGeneration) {
                // A reader reset invalidates earlier metadata groups. Ignore both stale
                // and speculative messages instead of assembling mixed generations.
                return;
            }
            if (!MySqlBinlogSplit.ID.equals(group.getSplitId())) {
                throw new IllegalArgumentException("Metadata delivered for another Binlog split");
            }
            if (binlogMetadata.addGroup(group)) {
                if (uncompletedBinlogSplit != null) {
                    MySqlBinlogSplit complete = uncompletedBinlogSplit.withFinishedSnapshotSplitInfos(
                            binlogMetadata.finishedSplits(uncompletedBinlogSplit.highWatermark()));
                    // Do not start Debezium before the complete fence set is installed.
                    try {
                        super.addSplits(List.of(complete));
                    } catch (Exception failure) {
                        throw new IllegalStateException("Cannot start assembled initial Binlog split", failure);
                    }
                    uncompletedBinlogSplit = null;
                    metrics.streaming(generation);
                }
                context.sendSourceEventToCoordinator(new BinlogSplitMetaAssembledEvent(binlogMetadata.generation()));
            } else if (group.getBinlogAssignmentGeneration() == binlogMetadata.generation()) {
                context.sendSourceEventToCoordinator(
                        new BinlogSplitMetaRequestEvent(binlogMetadata.nextGroupId(), binlogMetadata.generation()));
            }
        } else if (event instanceof MySqlResumeBinlogEvent resume) {
            List<FinishedSnapshotSplitInfo> finished = binlogMetadata.finishedSplits(resume.highWatermark());
            if (pendingBinlogUpdateGeneration >= 0 && suspendedBinlogSplit != null) {
                long updateGeneration = pendingBinlogUpdateGeneration;
                MySqlBinlogSplit resumed = suspendedBinlogSplit.startStreaming(resume.highWatermark(), finished);
                resumeEvent = resume;
                readerContext.wakeupSuspendedBinlogSplitReader();
                try {
                    super.addSplits(List.of(resumed));
                } catch (Exception failure) {
                    throw new IllegalStateException("Cannot resume Binlog after Snapshot metadata update", failure);
                }
                suspendedBinlogSplit = null;
                pendingBinlogUpdateGeneration = -1;
                metrics.streaming(generation);
                context.sendSourceEventToCoordinator(new BinlogSplitUpdateAckEvent(updateGeneration));
            } else {
                resumeEvent = resume;
                forEachActiveSplitState(state -> {
                    if (state.binlog()) state.resume(resume.highWatermark(), finished);
                });
                metrics.streaming(generation);
                splits.resumeBinlog(resume.highWatermark(), finished);
            }
        } else if (event instanceof io.yak.ops.connector.cdc.mysql.source.events.MySqlStopSnapshotEvent) {
            if (snapshotOnly && context.getIndexOfSubtask() == 0) {
                splits.stopSnapshotOnly();
            } else {
                splits.finishSnapshots();
            }
        } else {
            throw new IllegalArgumentException("Unsupported MySQL hybrid Reader event");
        }
    }

    @Override
    public void close() throws Exception {
        try {
            super.close();
        } finally {
            if (!initial) metrics.sourceClosed(generation);
        }
    }
}
