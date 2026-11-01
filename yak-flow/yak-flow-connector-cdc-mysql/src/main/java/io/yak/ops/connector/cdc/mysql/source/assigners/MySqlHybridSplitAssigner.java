package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.ChunkSplitterState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.TableId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Orchestrates the hybrid source's four stages without performing JDBC or Binlog I/O.
 *
 * <p>The transition from HANDOFF to STREAMING is gated by a completed checkpoint
 * containing all completed snapshot split watermarks, not by a final split delivery acknowledgment.
 */
public final class MySqlHybridSplitAssigner implements MySqlSplitAssigner {

    private final MySqlSnapshotSplitAssigner snapshots;
    private final boolean snapshotOnly;
    private final int splitMetaGroupSize;
    private MySqlBinlogSplit pendingBinlog;
    private boolean binlogAssigned;
    private HybridPendingSplitsState.Phase phase;
    private DebeziumOffset low;
    private DebeziumOffset high;
    private DebeziumOffset schemaHistoryWatermark;
    private byte[] schemaHistory = new byte[0];
    private Long handoffCheckpointId;
    private boolean binlogSplitMetaAssembled;
    private long binlogAssignmentGeneration;
    private boolean releaseSnapshotMetadataEnabled;
    private Long releaseMetadataCheckpointId;

    public MySqlHybridSplitAssigner(StartupOptions startupOptions, HybridPendingSplitsState restored) {
        this(startupOptions, restored, Integer.MAX_VALUE);
    }

    public MySqlHybridSplitAssigner(
            StartupOptions startupOptions, HybridPendingSplitsState restored, int splitMetaGroupSize) {
        Objects.requireNonNull(startupOptions, "startupOptions");
        if (splitMetaGroupSize <= 0) {
            throw new IllegalArgumentException("Invalid Binlog metadata group size");
        }
        this.splitMetaGroupSize = splitMetaGroupSize;
        if (startupOptions.isStreamOnly()) {
            throw new IllegalArgumentException("MySQL Hybrid Source requires snapshot startup");
        }
        snapshotOnly = startupOptions.isSnapshotOnly();
        if (restored == null) {
            snapshots = new MySqlSnapshotSplitAssigner();
            pendingBinlog = null;
            binlogAssigned = false;
            phase = HybridPendingSplitsState.Phase.SNAPSHOT;
        } else {
            phase = restored.phase();
            low = restored.lowWatermark();
            high = restored.highWatermark();
            schemaHistoryWatermark = restored.schemaHistoryWatermark();
            schemaHistory = restored.schemaHistory();
            pendingBinlog = restored.pendingBinlog();
            binlogAssigned = restored.isBinlogSplitAssigned();
            if (binlogAssigned && pendingBinlog != null) {
                throw new IllegalArgumentException("Binlog assignment and pending split state conflict");
            }
            snapshots = new MySqlSnapshotSplitAssigner(restored.getSnapshotPendingSplits());
        }
    }

    @Override
    public void open() {
        snapshots.open();
    }

    /** Initializes Snapshot table discovery and reconciles restored table state. */
    public boolean initializeTables(
            List<TableId> tables, boolean scanNewlyAddedTablesEnabled, boolean releaseSnapshotMetadataEnabled) {
        this.releaseSnapshotMetadataEnabled = releaseSnapshotMetadataEnabled;
        boolean started =
                snapshots.initializeTables(tables, scanNewlyAddedTablesEnabled, releaseSnapshotMetadataEnabled);
        if (snapshots.getAssignerStatus() == AssignerStatus.NEWLY_ADDED_ASSIGNING_SNAPSHOT_FINISHED
                && !snapshots.isSnapshotMetaReleased()) {
            refreshHighWatermark();
        } else if (!binlogAssigned
                && pendingBinlog == null
                && phase == HybridPendingSplitsState.Phase.HANDOFF
                && snapshots.allFinished()
                && !snapshots.finishedSplits().isEmpty()) {
            // A restored INITIAL checkpoint may have filtered out a completed table.
            // The original maximum HIGH then no longer describes the captured chunks.
            refreshHighWatermark();
        }
        return started;
    }

    public boolean isSnapshotMetaReleased() {
        return snapshots.isSnapshotMetaReleased();
    }

    public boolean isAssigningNewlyAddedTables() {
        return snapshots.getAssignerStatus() == AssignerStatus.NEWLY_ADDED_ASSIGNING;
    }

    public boolean isNewlyAddedSnapshotFinished() {
        return snapshots.getAssignerStatus() == AssignerStatus.NEWLY_ADDED_ASSIGNING_SNAPSHOT_FINISHED;
    }

    public boolean isNewlyAddedAssignmentFinished() {
        return snapshots.getAssignerStatus() == AssignerStatus.NEWLY_ADDED_ASSIGNING_FINISHED;
    }

    @Override
    public Optional<MySqlSplit> getNext() {
        if (AssignerStatus.isNewlyAddedAssigningSnapshotFinished(getAssignerStatus())) {
            return Optional.empty();
        }
        if (snapshots.noMoreSplits()) {
            return Optional.ofNullable(nextBinlog());
        }
        return snapshots.getNext();
    }

    /** Create the initial Binlog split only after completed Snapshot state is durable. */
    public MySqlBinlogSplit nextBinlog() {
        if (binlogAssigned || snapshotOnly) return null;
        if (pendingBinlog != null) {
            MySqlBinlogSplit next = pendingBinlog;
            pendingBinlog = null;
            binlogAssigned = true;
            return next;
        }
        if ((phase != HybridPendingSplitsState.Phase.HANDOFF && phase != HybridPendingSplitsState.Phase.SNAPSHOT)
                || !AssignerStatus.isInitialAssigningFinished(snapshots.getAssignerStatus())
                || !snapshots.allFinished()) return null;
        MySqlBinlogSplit next = createBinlogSplit();
        binlogAssigned = true;
        phase = HybridPendingSplitsState.Phase.STREAMING;
        return next;
    }

    /** Flink's minimum finished-chunk HIGH algorithm, with Debezium 2.7 history. */
    private MySqlBinlogSplit createBinlogSplit() {
        List<FinishedSnapshotSplitInfo> finished = snapshots.getFinishedSplitInfos();
        DebeziumOffset minimum =
                io.yak.ops.connector.cdc.mysql.source.utils.RecordUtils.getStartingOffsetOfBinlogSplit(finished);
        byte[] history = schemaHistoryForBinlog();
        // Preserve the actual position of the checkpointed Debezium 2.7 history.
        // If a removed table owned the earliest HIGH, seeking directly to the
        // remaining minimum would skip intervening DDL needed for schema recovery.
        DebeziumOffset start = schemaHistoryWatermark;
        if (start.compareTo(minimum) > 0) {
            throw new IllegalStateException("Snapshot schema history begins after the earliest retained Chunk");
        }
        DebeziumOffset maximum = finished.stream()
                .map(FinishedSnapshotSplitInfo::highWatermark)
                .max(DebeziumOffset::compareTo)
                .orElseThrow();
        if (high != null && !high.equals(maximum)) {
            throw new IllegalStateException("Snapshot high watermark conflicts with completed Chunk metadata");
        }
        high = maximum;
        // Flink CDC 3.7: do not put arbitrarily many finished chunks in the initial
        // Binlog assignment; the Reader fetches the missing groups from the coordinator.
        List<FinishedSnapshotSplitInfo> inline = finished.size() > splitMetaGroupSize ? List.of() : finished;
        return MySqlBinlogSplit.forCompletedSnapshot(start, history, maximum, inline, finished.size());
    }

    public MySqlSnapshotSplit nextSnapshot() {
        if (phase != HybridPendingSplitsState.Phase.SNAPSHOT
                && snapshots.getAssignerStatus() != AssignerStatus.NEWLY_ADDED_ASSIGNING) return null;
        return (MySqlSnapshotSplit) snapshots.getNext().orElse(null);
    }

    public void captureLow(DebeziumOffset offset) {
        if (phase != HybridPendingSplitsState.Phase.BOOTSTRAP) {
            if (!Objects.equals(low, offset)) {
                throw new IllegalStateException("Conflicting MySQL hybrid low watermark");
            }
            return;
        }
        low = Objects.requireNonNull(offset, "offset");
        phase = HybridPendingSplitsState.Phase.SNAPSHOT;
    }

    public void plan(List<MySqlSnapshotSplit> splits) {
        if (phase != HybridPendingSplitsState.Phase.SNAPSHOT) {
            throw new IllegalStateException("Cannot plan Snapshot after Binlog assignment");
        }
        snapshots.plan(splits);
    }

    @Override
    public boolean waitingForFinishedSplits() {
        return snapshots.waitingForFinishedSplits();
    }

    @Override
    public List<FinishedSnapshotSplitInfo> getFinishedSplitInfos() {
        return snapshots.getFinishedSplitInfos();
    }

    @Override
    public void onFinishedSplits(Map<String, DebeziumOffset> splitFinishedOffsets) {
        snapshots.onFinishedSplits(splitFinishedOffsets);
    }

    /**
     * Retain only the Schema History from the lowest completed Chunk HIGH. The matching
     * bounded Debezium backfill was running through that HIGH, unlike a schema-only
     * bootstrap started after Snapshot completion.
     */
    public void captureSnapshotSchemaHistory(
            Map<String, DebeziumOffset> completedOffsets, Map<String, byte[]> histories) {
        if (phase == HybridPendingSplitsState.Phase.STREAMING) {
            // New-table Snapshot metadata is reconciled with the running Binlog,
            // never used to replace the original initial starting history.
            return;
        }
        histories.forEach((splitId, history) -> {
            DebeziumOffset watermark = completedOffsets.get(splitId);
            if (watermark == null || history == null || history.length == 0) {
                throw new IllegalArgumentException("Snapshot history must belong to a completed Chunk");
            }
            if (schemaHistoryWatermark == null || watermark.compareTo(schemaHistoryWatermark) < 0) {
                schemaHistoryWatermark = watermark;
                schemaHistory = history.clone();
            }
        });
    }

    public byte[] schemaHistoryForBinlog() {
        if (schemaHistoryWatermark == null || schemaHistory.length == 0) {
            throw new IllegalStateException("Completed Snapshot has no recoverable Schema History");
        }
        DebeziumOffset minimum = snapshots.finishedSplits().stream()
                .map(FinishedSnapshotSplitInfo::highWatermark)
                .min(DebeziumOffset::compareTo)
                .orElseThrow(() -> new IllegalStateException("No finished Snapshot Chunk"));
        if (schemaHistoryWatermark.compareTo(minimum) > 0) {
            throw new IllegalStateException("Snapshot history begins after the earliest retained Chunk HIGH");
        }
        return schemaHistory.clone();
    }

    /** The Snapshot Assigner alone owns completion metadata and split attribution. */
    public FinishedSnapshotSplitInfo finishSnapshot(String splitId, DebeziumOffset highWatermark) {
        return snapshots.finishSnapshot(splitId, highWatermark);
    }

    public void finishSnapshot(FinishedSnapshotSplitInfo info) {
        snapshots.finishSnapshot(info);
    }

    public void appendPlan(MySqlSnapshotSplit split, int tableCount) {
        if (phase != HybridPendingSplitsState.Phase.SNAPSHOT
                && snapshots.getAssignerStatus() != AssignerStatus.NEWLY_ADDED_ASSIGNING) {
            throw new IllegalStateException("Cannot discover snapshot chunks outside a Snapshot assignment phase");
        }
        snapshots.appendPlan(split, tableCount);
    }

    /**
     * Delegates asynchronous Chunk discovery to the Snapshot Assigner.
     * Hybrid owns only bootstrap and Binlog handoff state.
     */
    public boolean discoverNextAsync(
            List<TableId> tables,
            MySqlChunkSplitter splitter,
            SplitEnumeratorContext<MySqlSplit> context,
            int maxPendingChunks,
            BiConsumer<MySqlSnapshotSplit, Integer> onDiscovered,
            BiConsumer<TableId, Throwable> onError) {
        if (phase != HybridPendingSplitsState.Phase.SNAPSHOT
                && snapshots.getAssignerStatus() != AssignerStatus.NEWLY_ADDED_ASSIGNING) return false;
        return snapshots.discoverNextAsync(tables, splitter, context, maxPendingChunks, onDiscovered, onError);
    }

    public List<FinishedSnapshotSplitInfo> finishedSplits() {
        return snapshots.finishedSplits();
    }

    public int nextTableIndex() {
        return snapshots.nextTableIndex();
    }

    public int nextChunkIndex() {
        return snapshots.nextChunkIndex();
    }

    public ChunkSplitterState getChunkSplitterState() {
        return snapshots.getChunkSplitterState();
    }

    public ChunkRange nextChunkRange() {
        return snapshots.nextChunkRange();
    }

    public int pendingSnapshotCount() {
        return snapshots.pending().size();
    }

    public boolean snapshotComplete() {
        return phase == HybridPendingSplitsState.Phase.SNAPSHOT && snapshots.allFinished();
    }

    public void captureHigh(DebeziumOffset offset) {
        if (!snapshotComplete()) {
            throw new IllegalStateException("High watermark requires all snapshot splits to finish");
        }
        high = Objects.requireNonNull(offset, "offset");
        phase = HybridPendingSplitsState.Phase.HANDOFF;
        binlogSplitMetaAssembled = false;
    }

    public boolean restoredHandoffReady() {
        return phase == HybridPendingSplitsState.Phase.HANDOFF && handoffCheckpointId == null;
    }

    /** Initial HANDOFF now creates a Binlog split after Checkpoint, not a resume event. */
    public void resumeRestoredHandoff() {
        throw new IllegalStateException("INITIAL Binlog is assigned after the Snapshot checkpoint");
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        AssignerStatus previousStatus = snapshots.getAssignerStatus();
        snapshots.notifyCheckpointComplete(checkpointId);
        AssignerStatus currentStatus = snapshots.getAssignerStatus();
        if (previousStatus == AssignerStatus.NEWLY_ADDED_ASSIGNING
                && currentStatus == AssignerStatus.NEWLY_ADDED_ASSIGNING_SNAPSHOT_FINISHED) {
            refreshHighWatermark();
        }

        if (phase == HybridPendingSplitsState.Phase.STREAMING
                && releaseSnapshotMetadataEnabled
                && !snapshots.isSnapshotMetaReleased()
                && AssignerStatus.isAssigningFinished(snapshots.getAssignerStatus())
                && releaseMetadataCheckpointId != null
                && checkpointId >= releaseMetadataCheckpointId) {
            snapshots.releaseSnapshotMetadata();
            schemaHistoryWatermark = null;
            schemaHistory = new byte[0];
            releaseMetadataCheckpointId = null;
        }
    }

    private void refreshHighWatermark() {
        DebeziumOffset maximum = snapshots.finishedSplits().stream()
                .map(FinishedSnapshotSplitInfo::highWatermark)
                .max(DebeziumOffset::compareTo)
                .orElseThrow(() -> new IllegalStateException("Completed Snapshot assignment has no watermarks"));
        if (low != null && low.compareTo(maximum) > 0) {
            throw new IllegalStateException("Snapshot High watermark moved behind the Binlog anchor");
        }
        high = maximum;
    }

    /**
     * Invalidate metadata-assembly ACKs from the previous Binlog Reader attempt.
     * The generation travels with each metadata group, as in Flink CDC.
     */
    public void onBinlogReaderReset() {
        binlogSplitMetaAssembled = false;
        handoffCheckpointId = null;
        binlogAssignmentGeneration++;
    }

    public long getBinlogAssignmentGeneration() {
        return binlogAssignmentGeneration;
    }

    /** Ignore stale or premature reader ACKs; only the current assignment may arm handoff. */
    public boolean onBinlogSplitMetaAssembled(long generation) {
        if (snapshots.isSnapshotMetaReleased()) return false;
        if (generation != binlogAssignmentGeneration
                || !binlogAssigned
                || (phase != HybridPendingSplitsState.Phase.HANDOFF
                        && phase != HybridPendingSplitsState.Phase.STREAMING)) {
            return false;
        }
        binlogSplitMetaAssembled = true;
        return true;
    }

    @Override
    public HybridPendingSplitsState snapshotState(long checkpointId) {
        if (checkpointId < 0) {
            throw new IllegalArgumentException("Checkpoint ID must not be negative");
        }
        var snapshotState = snapshots.snapshotState(checkpointId);
        if (phase == HybridPendingSplitsState.Phase.STREAMING
                && binlogSplitMetaAssembled
                && releaseSnapshotMetadataEnabled
                && !snapshots.isSnapshotMetaReleased()
                && AssignerStatus.isAssigningFinished(snapshots.getAssignerStatus())
                && releaseMetadataCheckpointId == null) {
            releaseMetadataCheckpointId = checkpointId;
        }
        return new HybridPendingSplitsState(
                snapshotState, binlogAssigned, pendingBinlog, phase, low, high, schemaHistoryWatermark, schemaHistory);
    }

    @Override
    public void addSplits(Collection<MySqlSplit> splits) {
        for (MySqlSplit split : splits) {
            if (split instanceof MySqlSnapshotSplit snapshot) {
                addBack(snapshot);
            } else if (split instanceof MySqlBinlogSplit binlogSplit) {
                addBack(binlogSplit);
            } else {
                throw new IllegalArgumentException("Unsupported returned MySQL split");
            }
        }
    }

    public void addBack(MySqlBinlogSplit split) {
        if (pendingBinlog != null || !binlogAssigned) {
            throw new IllegalStateException("Binlog is not assigned");
        }
        pendingBinlog = Objects.requireNonNull(split, "split");
        binlogAssigned = false;
        onBinlogReaderReset();
    }

    public void addBack(MySqlSnapshotSplit split) {
        snapshots.addBack(split);
    }

    public HybridPendingSplitsState.Phase phase() {
        return phase;
    }

    public DebeziumOffset low() {
        return low;
    }

    public DebeziumOffset high() {
        return high;
    }

    public boolean snapshotPlanned() {
        return snapshots.planned();
    }

    @Override
    public AssignerStatus getAssignerStatus() {
        return snapshots.getAssignerStatus();
    }

    @Override
    public void startAssignNewlyAddedTables() {
        snapshots.startAssignNewlyAddedTables();
    }

    public void addDiscoveredTable(TableId table) {
        snapshots.addDiscoveredTable(table);
    }

    @Override
    public boolean noMoreSplits() {
        return snapshots.noMoreSplits() && (snapshotOnly || binlogAssigned);
    }

    @Override
    public void onBinlogSplitUpdated() {
        snapshots.onBinlogSplitUpdated();
    }

    @Override
    public void close() {
        snapshots.close();
    }
}
