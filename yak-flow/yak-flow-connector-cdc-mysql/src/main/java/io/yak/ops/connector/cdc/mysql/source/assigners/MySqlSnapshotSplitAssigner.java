/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.ChunkSplitterState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.SnapshotPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.TableId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Owns snapshot split discovery, assignment, completion and the checkpointed chunk cursor.
 *
 * <p>Follows Apache Flink CDC's MySqlSnapshotSplitAssigner (source pinned to
 * b806d26762862a76dc91ac81f16dbd7f6ec62821). The JDBC discovery thread remains in
 * YakFlow's SourceEnumerator; all state transitions are serialized on its coordinator thread.
 * The existing YakFlow SnapshotPendingSplitsState wire format is preserved until a separate
 * checkpoint migration can be validated. Debezium 2.7 offsets remain reader-owned.
 */
public final class MySqlSnapshotSplitAssigner implements MySqlSplitAssigner {
    private static final int MAX_SPLITS = 10_000;

    private final Deque<MySqlSnapshotSplit> remainingSplits = new ArrayDeque<>();
    private final Map<String, MySqlSnapshotSplit> assignedSplits = new LinkedHashMap<>();
    private final Map<String, FinishedSnapshotSplitInfo> finishedSplitInfos = new LinkedHashMap<>();
    private final Set<String> ignoredSplitIds = new LinkedHashSet<>();
    private final Deque<TableId> remainingTables = new ArrayDeque<>();
    private final Set<TableId> alreadyProcessedTables = new LinkedHashSet<>();
    private int totalSnapshotSplits;
    private boolean snapshotPlanned;
    private int nextTableIndex;
    private int nextChunkIndex;
    private ChunkRange nextChunkRange;
    private boolean planning;
    private boolean closed;
    private boolean restoredFromState;
    private boolean tablesInitialized;
    private boolean remainingTablesCheckpointed;
    private boolean tableIdCaseSensitive = true;
    private boolean snapshotMetaReleased;
    private boolean scanNewlyAddedTablesEnabled;
    private boolean releaseSnapshotMetadataEnabled;
    private AssignerStatus assignerStatus = AssignerStatus.INITIAL_ASSIGNING;
    private Long checkpointIdToFinish;
    private List<TableId> capturedTables = List.of();

    public MySqlSnapshotSplitAssigner() {}

    public MySqlSnapshotSplitAssigner(SnapshotPendingSplitsState restored) {
        Objects.requireNonNull(restored, "restored");
        restoredFromState = true;
        remainingSplits.addAll(restored.getRemainingSplits());
        assignedSplits.putAll(restored.getAssignedSplits());
        restored.getFinishedSplitInfos().forEach(info -> finishedSplitInfos.put(info.splitId(), info));
        totalSnapshotSplits = restored.totalSnapshotSplits();
        snapshotPlanned = restored.snapshotPlanned();
        nextTableIndex = restored.nextTableIndex();
        nextChunkIndex = restored.nextChunkIndex();
        nextChunkRange = restored.nextChunkRange();
        alreadyProcessedTables.addAll(restored.getAlreadyProcessedTables());
        remainingTables.addAll(restored.getRemainingTables());
        assignerStatus = restored.getSnapshotAssignerStatus();
        tableIdCaseSensitive = restored.isTableIdCaseSensitive();
        remainingTablesCheckpointed = restored.isRemainingTablesCheckpointed();
        snapshotMetaReleased = restored.isSnapshotMetaReleased();
    }

    @Override
    public void open() {}

    /** Reconciles checkpointed table work with the current configured source tables. */
    public boolean initializeTables(
            List<TableId> tables, boolean scanNewlyAddedTablesEnabled, boolean releaseSnapshotMetadataEnabled) {
        Objects.requireNonNull(tables, "tables");
        if (tablesInitialized) return false;
        if (scanNewlyAddedTablesEnabled && releaseSnapshotMetadataEnabled) {
            throw new IllegalArgumentException(
                    "Snapshot metadata release cannot be enabled with newly-added-table discovery");
        }
        this.scanNewlyAddedTablesEnabled = scanNewlyAddedTablesEnabled;
        this.releaseSnapshotMetadataEnabled = releaseSnapshotMetadataEnabled;
        LinkedHashSet<TableId> unique = new LinkedHashSet<>(tables);
        if (unique.contains(null) || unique.size() != tables.size())
            throw new IllegalArgumentException("MySQL snapshot table list contains null or duplicate identifiers");
        capturedTables = List.copyOf(unique);
        tablesInitialized = true;
        if (!restoredFromState) {
            if (!snapshotPlanned && remainingTables.isEmpty()) remainingTables.addAll(capturedTables);
            remainingTablesCheckpointed = true;
            return false;
        }
        if (!remainingTablesCheckpointed) restoreLegacyTableState();
        // A checkpoint can capture all finished splits immediately before notifyCheckpointComplete
        // advances the in-memory status. Since a restored checkpoint is durable, reconcile that
        // state before looking for newly configured tables.
        if (AssignerStatus.isAssigningSnapshotSplits(assignerStatus) && allFinished()) {
            assignerStatus = assignerStatus.onFinish();
            checkpointIdToFinish = null;
        }
        Set<TableId> known = knownTables();
        LinkedHashSet<TableId> current = new LinkedHashSet<>(capturedTables);
        // Flink CDC distinguishes table discovery from checkpoint recovery. A static source
        // must not reinterpret an in-flight checkpoint against a different capture set:
        // dropping the old work or ignoring newly configured tables could silently lose rows.
        // Explicitly enabled dynamic-table discovery owns those changes below.
        if (!scanNewlyAddedTablesEnabled && !known.equals(current)) {
            throw new IllegalStateException(
                    "Restored MySQL Snapshot checkpoint table set differs from the configured capture set; "
                            + "enable scan newly added tables or restore the original source definition");
        }
        LinkedHashSet<TableId> removed = new LinkedHashSet<>(known);
        removed.removeAll(current);
        removeTablesNoLongerCaptured(removed);
        LinkedHashSet<TableId> newlyAdded = new LinkedHashSet<>(current);
        newlyAdded.removeAll(known);
        if (scanNewlyAddedTablesEnabled
                && !newlyAdded.isEmpty()
                && AssignerStatus.isAssigningFinished(assignerStatus)) {
            if (snapshotMetaReleased)
                throw new IllegalStateException("Cannot discover new tables after Snapshot metadata was released");
            remainingTables.addAll(newlyAdded);
            snapshotPlanned = false;
            startAssignNewlyAddedTables();
            return true;
        }
        return false;
    }

    private void restoreLegacyTableState() {
        assignedSplits.values().forEach(split -> alreadyProcessedTables.add(split.tableId()));
        finishedSplitInfos.values().forEach(info -> alreadyProcessedTables.add(info.tableId()));
        if (snapshotPlanned) {
            if (remainingSplits.isEmpty()
                    && assignedSplits.isEmpty()
                    && finishedSplitInfos.size() == totalSnapshotSplits)
                assignerStatus = AssignerStatus.INITIAL_ASSIGNING_FINISHED;
            remainingTablesCheckpointed = true;
            return;
        }
        TableId cursorTable = nextChunkRange == null ? null : nextChunkRange.tableId();
        int start = cursorTable == null
                ? Math.min(nextTableIndex, capturedTables.size())
                : capturedTables.indexOf(cursorTable);
        if (cursorTable != null && start < 0) start = Math.min(nextTableIndex, capturedTables.size());
        for (int i = start; i < capturedTables.size(); i++) {
            TableId table = capturedTables.get(i);
            if (Objects.equals(table, cursorTable) || !hasPlannedSplitsForTable(table)) {
                if (!remainingTables.contains(table)) remainingTables.addLast(table);
            }
        }
        remainingTablesCheckpointed = true;
    }

    private boolean hasPlannedSplitsForTable(TableId table) {
        return remainingSplits.stream().anyMatch(split -> split.tableId().equals(table))
                || assignedSplits.values().stream()
                        .anyMatch(split -> split.tableId().equals(table))
                || finishedSplitInfos.values().stream()
                        .anyMatch(info -> info.tableId().equals(table));
    }

    private Set<TableId> knownTables() {
        LinkedHashSet<TableId> known = new LinkedHashSet<>(alreadyProcessedTables);
        known.addAll(remainingTables);
        remainingSplits.forEach(split -> known.add(split.tableId()));
        assignedSplits.values().forEach(split -> known.add(split.tableId()));
        finishedSplitInfos.values().forEach(info -> known.add(info.tableId()));
        if (nextChunkRange != null) known.add(nextChunkRange.tableId());
        return known;
    }

    private void removeTablesNoLongerCaptured(Set<TableId> removed) {
        if (removed.isEmpty()) return;
        if (snapshotMetaReleased) {
            alreadyProcessedTables.removeAll(removed);
            remainingTables.removeIf(removed::contains);
            return;
        }
        int count = 0;
        for (var iterator = remainingSplits.iterator(); iterator.hasNext(); ) {
            if (removed.contains(iterator.next().tableId())) {
                iterator.remove();
                count++;
            }
        }
        for (var iterator = assignedSplits.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (removed.contains(entry.getValue().tableId())) {
                ignoredSplitIds.add(entry.getKey());
                iterator.remove();
                count++;
            }
        }
        for (var iterator = finishedSplitInfos.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (removed.contains(entry.getValue().tableId())) {
                iterator.remove();
                count++;
            }
        }
        remainingTables.removeIf(removed::contains);
        alreadyProcessedTables.removeAll(removed);
        totalSnapshotSplits -= count;
        if (totalSnapshotSplits < 0) throw new IllegalStateException("Removed more Snapshot splits than were planned");
        if (nextChunkRange != null && removed.contains(nextChunkRange.tableId())) {
            nextChunkRange = null;
            nextChunkIndex = 0;
            nextTableIndex = 0;
        }
        snapshotPlanned = remainingTables.isEmpty() && nextChunkRange == null;
    }

    /**
     * Flink CDC-style asynchronous Chunk discovery owned by the Snapshot Assigner.
     *
     * <p>The coordinator supplies the existing async executor, while this assigner owns
     * discovery scheduling, outstanding-work gating and the durable ChunkSplitterState.
     */
    public boolean discoverNextAsync(
            List<TableId> tables,
            MySqlChunkSplitter splitter,
            SplitEnumeratorContext<MySqlSplit> context,
            int maxPendingChunks,
            BiConsumer<MySqlSnapshotSplit, Integer> onDiscovered,
            BiConsumer<TableId, Throwable> onError) {
        Objects.requireNonNull(tables, "tables");
        Objects.requireNonNull(splitter, "splitter");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(onDiscovered, "onDiscovered");
        Objects.requireNonNull(onError, "onError");
        if (!tablesInitialized) initializeTables(tables, false, false);
        if (closed
                || planning
                || snapshotPlanned
                || !AssignerStatus.isAssigningSnapshotSplits(assignerStatus)
                || remainingSplits.size() >= maxPendingChunks) return false;
        TableId table = nextChunkRange == null ? remainingTables.peekFirst() : nextChunkRange.tableId();
        if (table == null) {
            snapshotPlanned = true;
            return false;
        }
        int tableIndex = capturedTables.indexOf(table);
        int chunkIndex = nextChunkRange == null ? 0 : nextChunkIndex;
        if (tableIndex < 0)
            throw new IllegalStateException("Snapshot table is not in the current configured table list");
        if ((chunkIndex == 0) != (nextChunkRange == null))
            throw new IllegalStateException("Snapshot Chunk discovery cursor does not match its index");
        ChunkSplitterState cursor = getChunkSplitterState();
        planning = true;
        try {
            context.callAsync(() -> splitter.planNext(table, tableIndex, cursor), (split, failure) -> {
                planning = false;
                if (closed) return;
                if (failure != null) {
                    onError.accept(table, failure);
                    return;
                }
                try {
                    appendPlan(split, tables.size());
                } catch (RuntimeException invalid) {
                    onError.accept(table, invalid);
                    return;
                }
                onDiscovered.accept(split, chunkIndex + 1);
            });
        } catch (RuntimeException schedulingFailure) {
            planning = false;
            throw schedulingFailure;
        }
        return true;
    }

    /** Registers all the supplied splits and finishes discovery. */
    public void plan(List<MySqlSnapshotSplit> splits) {
        append(splits, true);
        remainingTables.clear();
        nextChunkRange = null;
        nextChunkIndex = 0;
        if (!capturedTables.isEmpty()) nextTableIndex = capturedTables.size();
    }

    /** Adds newly discovered snapshot splits without declaring the table scan complete. */
    public void append(List<MySqlSnapshotSplit> splits, boolean lastPage) {
        Objects.requireNonNull(splits, "splits");
        if (snapshotPlanned || (long) totalSnapshotSplits + splits.size() > MAX_SPLITS) {
            throw new IllegalStateException("Snapshot discovery is complete or exceeds the split limit");
        }
        Set<String> ids = new LinkedHashSet<>(assignedSplits.keySet());
        ids.addAll(finishedSplitInfos.keySet());
        remainingSplits.forEach(split -> ids.add(split.splitId()));
        for (MySqlSnapshotSplit split : splits) {
            requireSplit(split);
            if (!ids.add(split.splitId())) {
                throw new IllegalArgumentException("Duplicate snapshot split ID");
            }
        }
        remainingSplits.addAll(splits);
        totalSnapshotSplits += splits.size();
        snapshotPlanned = lastPage;
    }

    /**
     * Advances the checkpointed discovery cursor only after the discovered split has been
     * accepted. The cursor always denotes the next half-open Chunk boundary.
     */
    public void appendPlan(MySqlSnapshotSplit split, int tableCount) {
        Objects.requireNonNull(split, "split");
        ChunkRange range = split.chunkRange();
        if (range == null) throw new IllegalStateException("Invalid snapshot discovery transition");
        if (capturedTables.isEmpty()) {
            capturedTables = List.of(range.tableId());
            tablesInitialized = true;
        }
        if (remainingTables.isEmpty() && !snapshotPlanned) remainingTables.add(range.tableId());
        if (remainingTables.isEmpty() || !remainingTables.peekFirst().equals(range.tableId()))
            throw new IllegalStateException("Snapshot Chunk does not belong to the head of the table queue");
        if ((nextChunkIndex == 0 && (nextChunkRange != null || range.start() != null))
                || (nextChunkIndex > 0
                        && (nextChunkRange == null
                                || nextChunkRange.end() != null
                                || !nextChunkRange.tableId().equals(range.tableId())
                                || !nextChunkRange.column().equals(range.column())
                                || nextChunkRange.type() != range.type()
                                || !Objects.equals(nextChunkRange.start(), range.start())))) {
            throw new IllegalStateException("Snapshot range differs from the durable discovery cursor");
        }
        int tableIndex = capturedTables.indexOf(range.tableId());
        if (tableIndex < 0) throw new IllegalStateException("Snapshot range refers to an unknown table");
        append(List.of(split), false);
        if (range.last()) {
            remainingTables.removeFirst();
            nextTableIndex = remainingTables.isEmpty()
                    ? Math.max(nextTableIndex, tableIndex + 1)
                    : capturedTables.indexOf(remainingTables.peekFirst());
            nextChunkIndex = 0;
            nextChunkRange = null;
            snapshotPlanned = remainingTables.isEmpty();
        } else {
            nextTableIndex = tableIndex;
            nextChunkIndex++;
            nextChunkRange = new ChunkRange(range.tableId(), range.column(), range.type(), range.end(), null);
            snapshotPlanned = false;
        }
    }

    @Override
    public Optional<MySqlSplit> getNext() {
        return Optional.ofNullable(next());
    }

    /** Assigning a split is never evidence that its reader has completed it. */
    public MySqlSnapshotSplit next() {
        MySqlSnapshotSplit split = remainingSplits.pollFirst();
        if (split != null) {
            assignedSplits.put(split.splitId(), split);
            alreadyProcessedTables.add(split.tableId());
        }
        return split;
    }

    @Override
    public boolean waitingForFinishedSplits() {
        return !allFinished();
    }

    /**
     * Flink CDC exposes complete Binlog filtering metadata only after all snapshot splits
     * have finished; partial metadata is available separately for Reader ACKs.
     */
    @Override
    public List<FinishedSnapshotSplitInfo> getFinishedSplitInfos() {
        if (waitingForFinishedSplits()) {
            throw new IllegalStateException("Snapshot splits have not all reported their high watermarks");
        }
        return snapshotMetaReleased ? List.of() : finishedSplits();
    }

    @Override
    public void onFinishedSplits(Map<String, DebeziumOffset> offsets) {
        Objects.requireNonNull(offsets, "offsets");
        // Validate an entire report before mutating any assignment state.
        List<FinishedSnapshotSplitInfo> accepted = new ArrayList<>();
        for (var entry : offsets.entrySet()) {
            if (ignoredSplitIds.contains(entry.getKey())) continue;
            accepted.add(finishedInfo(entry.getKey(), entry.getValue()));
        }
        accepted.forEach(this::finishSnapshot);
    }

    /** Builds completion metadata from coordinator-owned split boundaries. */
    public FinishedSnapshotSplitInfo finishSnapshot(String splitId, DebeziumOffset highWatermark) {
        FinishedSnapshotSplitInfo info = finishedInfo(splitId, highWatermark);
        finishSnapshot(info);
        return info;
    }

    private FinishedSnapshotSplitInfo finishedInfo(String splitId, DebeziumOffset highWatermark) {
        Objects.requireNonNull(splitId, "splitId");
        Objects.requireNonNull(highWatermark, "highWatermark");
        if (snapshotMetaReleased) throw new IllegalStateException("Snapshot metadata has already been released");
        FinishedSnapshotSplitInfo previous = finishedSplitInfos.get(splitId);
        if (previous != null) {
            if (!previous.highWatermark().equals(highWatermark)) {
                throw new IllegalStateException("Conflicting completed snapshot high watermark");
            }
            return previous;
        }
        MySqlSnapshotSplit assigned = assignedSplits.get(splitId);
        if (assigned == null) throw new IllegalArgumentException("Unknown completed snapshot split");
        return new FinishedSnapshotSplitInfo(
                assigned.tableId(),
                splitId,
                assigned.lowerInclusive(),
                assigned.upperExclusive(),
                highWatermark,
                assigned.chunkRange());
    }

    /** Accepts one finished Chunk only after its ID, boundaries and watermark agree. */
    public void finishSnapshot(FinishedSnapshotSplitInfo info) {
        Objects.requireNonNull(info, "info");
        if (snapshotMetaReleased) throw new IllegalStateException("Snapshot metadata has already been released");
        FinishedSnapshotSplitInfo previous = finishedSplitInfos.get(info.splitId());
        if (previous != null) {
            if (!previous.equals(info)) throw new IllegalStateException("Conflicting completed snapshot metadata");
            return;
        }
        MySqlSnapshotSplit assigned = assignedSplits.get(info.splitId());
        if (assigned == null
                || !assigned.tableId().equals(info.tableId())
                || !Objects.equals(assigned.lowerInclusive(), info.lowerInclusive())
                || !Objects.equals(assigned.upperExclusive(), info.upperExclusive())
                || !Objects.equals(assigned.chunkRange(), info.chunkRange())) {
            throw new IllegalArgumentException("Completed chunk differs from its assigned range");
        }
        assignedSplits.remove(info.splitId());
        finishedSplitInfos.put(info.splitId(), info);
    }

    public void addBack(MySqlSnapshotSplit returned) {
        requireSplit(returned);
        MySqlSnapshotSplit original = assignedSplits.get(returned.splitId());
        if (original == null
                || !original.tableId().equals(returned.tableId())
                || !Objects.equals(original.chunkRange(), returned.chunkRange())
                || !Objects.equals(original.lowerInclusive(), returned.lowerInclusive())
                || !Objects.equals(original.upperExclusive(), returned.upperExclusive())) {
            throw new IllegalArgumentException("Returned snapshot split was not assigned or its range changed");
        }
        // Incomplete snapshot output is never durable. A failed Reader rescans the original
        // immutable Chunk rather than persisting partially emitted row images in coordinator state.
        assignedSplits.remove(original.splitId());
        remainingSplits.addFirst(original);
    }

    @Override
    public void addSplits(Collection<MySqlSplit> splits) {
        for (MySqlSplit split : splits) {
            if (!(split instanceof MySqlSnapshotSplit snapshot)) {
                throw new IllegalArgumentException("Snapshot assigner only accepts snapshot splits");
            }
            addBack(snapshot);
        }
    }

    public boolean allFinished() {
        return snapshotPlanned
                && remainingSplits.isEmpty()
                && assignedSplits.isEmpty()
                && remainingTables.isEmpty()
                && nextChunkRange == null
                && (snapshotMetaReleased || finishedSplitInfos.size() == totalSnapshotSplits);
    }

    public boolean planned() {
        return snapshotPlanned;
    }

    public int total() {
        return totalSnapshotSplits;
    }

    public List<MySqlSnapshotSplit> pending() {
        return List.copyOf(remainingSplits);
    }

    public Map<String, MySqlSnapshotSplit> assigned() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(assignedSplits));
    }

    public Set<String> finished() {
        return Set.copyOf(finishedSplitInfos.keySet());
    }

    public List<FinishedSnapshotSplitInfo> finishedSplits() {
        return snapshotMetaReleased ? List.of() : List.copyOf(finishedSplitInfos.values());
    }

    public List<TableId> remainingTables() {
        return List.copyOf(remainingTables);
    }

    public List<TableId> alreadyProcessedTables() {
        return List.copyOf(alreadyProcessedTables);
    }

    public int nextTableIndex() {
        return nextTableIndex;
    }

    public int nextChunkIndex() {
        return nextChunkIndex;
    }

    public ChunkRange nextChunkRange() {
        return nextChunkRange;
    }

    public ChunkSplitterState getChunkSplitterState() {
        if (nextChunkRange == null) return ChunkSplitterState.NO_SPLITTING_TABLE_STATE;
        return new ChunkSplitterState(
                nextChunkRange.tableId(),
                ChunkSplitterState.ChunkBound.middleOf(nextChunkRange.start()),
                nextChunkIndex);
    }

    @Override
    public SnapshotPendingSplitsState snapshotState(long checkpointId) {
        if (checkpointId < 0) throw new IllegalArgumentException("Checkpoint ID must not be negative");
        SnapshotPendingSplitsState state = new SnapshotPendingSplitsState(
                snapshotPlanned,
                totalSnapshotSplits,
                pending(),
                assigned(),
                finished(),
                finishedSplits(),
                nextTableIndex,
                nextChunkIndex,
                nextChunkRange,
                alreadyProcessedTables(),
                remainingTables(),
                assignerStatus,
                tableIdCaseSensitive,
                true,
                snapshotMetaReleased);
        if (checkpointIdToFinish == null && AssignerStatus.isAssigningSnapshotSplits(assignerStatus) && allFinished()) {
            checkpointIdToFinish = checkpointId;
        }
        return state;
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        if (checkpointIdToFinish != null
                && checkpointId >= checkpointIdToFinish
                && AssignerStatus.isAssigningSnapshotSplits(assignerStatus)
                && allFinished()) {
            assignerStatus = assignerStatus.onFinish();
            checkpointIdToFinish = null;
        }
    }

    @Override
    public AssignerStatus getAssignerStatus() {
        return assignerStatus;
    }

    @Override
    public void startAssignNewlyAddedTables() {
        if (!AssignerStatus.isAssigningFinished(assignerStatus))
            throw new IllegalStateException(
                    "New tables can only be assigned after the previous assignment is finished");
        if (snapshotMetaReleased || releaseSnapshotMetadataEnabled)
            throw new IllegalStateException("Cannot assign newly added tables after Snapshot metadata release");
        if (remainingTables.isEmpty())
            throw new IllegalStateException("Newly-added-table assignment requires a non-empty table queue");
        snapshotPlanned = false;
        checkpointIdToFinish = null;
        assignerStatus = assignerStatus.startAssignNewlyTables();
    }

    /** Queues a table only after the schema coordinator has acknowledged all reader subtasks. */
    public void addDiscoveredTable(TableId table) {
        if (!scanNewlyAddedTablesEnabled || capturedTables.contains(table)) {
            throw new IllegalArgumentException("Table is already captured or dynamic discovery is disabled");
        }
        if (!AssignerStatus.isAssigningFinished(assignerStatus)) {
            throw new IllegalStateException("Previous table assignment has not completed");
        }
        List<TableId> updated = new ArrayList<>(capturedTables);
        updated.add(table);
        capturedTables = List.copyOf(updated);
        remainingTables.add(table);
        startAssignNewlyAddedTables();
    }

    @Override
    public boolean noMoreSplits() {
        return snapshotPlanned && remainingSplits.isEmpty() && remainingTables.isEmpty() && nextChunkRange == null;
    }

    @Override
    public void onBinlogSplitUpdated() {
        if (assignerStatus == AssignerStatus.NEWLY_ADDED_ASSIGNING_FINISHED) return;
        if (assignerStatus != AssignerStatus.NEWLY_ADDED_ASSIGNING_SNAPSHOT_FINISHED)
            throw new IllegalStateException(
                    "Binlog split update ACK is invalid while Snapshot Assigner is " + assignerStatus);
        assignerStatus = assignerStatus.onBinlogSplitUpdated();
    }

    /** Releases per-split metadata after a durable Binlog handoff checkpoint. */
    public void releaseSnapshotMetadata() {
        if (snapshotMetaReleased) return;
        if (!releaseSnapshotMetadataEnabled)
            throw new IllegalStateException("Snapshot metadata release is not enabled");
        if (!AssignerStatus.isAssigningFinished(assignerStatus) || !allFinished())
            throw new IllegalStateException("Snapshot metadata cannot be released before assignment is complete");
        assignedSplits.clear();
        finishedSplitInfos.clear();
        ignoredSplitIds.clear();
        snapshotMetaReleased = true;
    }

    public boolean isSnapshotMetaReleased() {
        return snapshotMetaReleased;
    }

    @Override
    public void close() {
        closed = true;
    }

    private static void requireSplit(MySqlSnapshotSplit split) {
        Objects.requireNonNull(split, "split");
        if (split.splitId() == null || split.splitId().isBlank() || split.tableId() == null) {
            throw new IllegalArgumentException("Snapshot split requires a stable ID and table");
        }
    }
}
