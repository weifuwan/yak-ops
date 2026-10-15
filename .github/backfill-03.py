from pathlib import Path
C = Path('yak-flow/yak-flow-connector-cdc-mysql/src/main/java/io/yak/ops/connector/cdc/mysql/source')
T = Path('yak-flow/yak-flow-connector-cdc-mysql/src/test/java/io/yak/ops/connector/cdc/mysql/source')
def put(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.strip() + '\n')
def edit(path, old, new):
    text = path.read_text()
    if old not in text: raise AssertionError(f'Missing patch anchor in {path}: {old[:100]}')
    path.write_text(text.replace(old, new))

p = C/'assigner/MySqlSnapshotSplitAssigner.java'
a = p.read_text().index('    /** Registers an immutable complete plan')
b = p.read_text().index('    /** Assigns a split', a)
s = p.read_text(); p.write_text(s[:a] + '''    /** Registers a complete plan for direct callers. */
    public void plan(List<MySqlSnapshotSplit> ranges) { append(ranges, true); }

    /** Appends a bounded discovery page without treating undiscovered tables as finished. */
    public void append(List<MySqlSnapshotSplit> ranges, boolean lastPage) {
        if (planned || total + ranges.size() > 10_000) {
            throw new IllegalStateException("Snapshot discovery is complete or exceeds the checkpoint chunk limit");
        }
        Set<String> ids = new LinkedHashSet<>(assigned.keySet());
        ids.addAll(finished);
        pending.forEach(split -> ids.add(split.splitId()));
        for (MySqlSnapshotSplit split : ranges) {
            if (!ids.add(split.splitId())) throw new IllegalArgumentException("Duplicate MySQL snapshot split ID");
        }
        pending.addAll(ranges);
        total += ranges.size();
        planned = lastPage;
    }

''' + s[b:])

p = C/'enumerator/MySqlHybridEnumeratorState.java'
edit(p, 'import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;', 'import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;\nimport io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;')
edit(p, 'Set<String> finishedSnapshots) {', '''Set<String> finishedSnapshots,
        List<FinishedSnapshotSplitInfo> finishedSplitInfos,
        int nextTableIndex,
        int nextChunkIndex,
        Long nextChunkLower) {''')
edit(p, 'Objects.requireNonNull(fingerprint, "fingerprint");', '''Objects.requireNonNull(fingerprint, "fingerprint");
        finishedSplitInfos = List.copyOf(Objects.requireNonNull(finishedSplitInfos, "finishedSplitInfos"));
        if (nextTableIndex < 0 || nextChunkIndex < 0 || nextChunkIndex > 10_000
                || (nextChunkIndex == 0 && nextChunkLower != null)
                || (nextChunkIndex > 0 && nextChunkLower == null)) {
            throw new IllegalArgumentException("Invalid snapshot discovery position");
        }''')
edit(p, 'if (binlogAssigned == (pendingBinlog != null)', '''Set<String> completedIds = new java.util.HashSet<>();
        for (FinishedSnapshotSplitInfo info : finishedSplitInfos) {
            if (!completedIds.add(info.splitId())) throw new IllegalArgumentException("Duplicate completed chunk metadata");
        }
        if (!completedIds.equals(finishedSnapshots)) throw new IllegalArgumentException("Incomplete chunk High metadata");
        Set<String> allIds = new java.util.HashSet<>(finishedSnapshots);
        for (MySqlSnapshotSplit split : pendingSnapshots) {
            if (!allIds.add(split.splitId())) throw new IllegalArgumentException("Duplicate pending chunk");
        }
        for (var entry : assignedSnapshots.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().splitId()) || !allIds.add(entry.getKey())) {
                throw new IllegalArgumentException("Duplicate or invalid assigned chunk");
            }
        }
        if (binlogAssigned == (pendingBinlog != null)''')
edit(p, '|| totalSnapshotSplits < 0', '|| totalSnapshotSplits < 0 || totalSnapshotSplits > 10_000')

p = C/'assigner/MySqlHybridSplitAssigner.java'
edit(p, 'import java.util.List;', '''import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;''')
edit(p, 'private Long handoffCheckpointId;', '''private Long handoffCheckpointId;
    private final Map<String, FinishedSnapshotSplitInfo> completed = new LinkedHashMap<>();
    private int nextTableIndex;
    private int nextChunkIndex;
    private Long nextChunkLower;''')
edit(p, 'phase = restored.phase();', '''restored.finishedSplitInfos().forEach(info -> completed.put(info.splitId(), info));
            nextTableIndex = restored.nextTableIndex();
            nextChunkIndex = restored.nextChunkIndex();
            nextChunkLower = restored.nextChunkLower();
            phase = restored.phase();''')
edit(p, '''public void finishSnapshot(String splitId) {
        snapshots.complete(splitId);
    }''', '''public void finishSnapshot(FinishedSnapshotSplitInfo info) {
        FinishedSnapshotSplitInfo previous = completed.get(info.splitId());
        if (previous != null) {
            if (!previous.equals(info)) throw new IllegalStateException("Conflicting completed chunk High watermark");
            return;
        }
        MySqlSnapshotSplit original = snapshots.assigned().get(info.splitId());
        if (original == null || !original.tableId().equals(info.tableId())
                || !Objects.equals(original.lowerInclusive(), info.lowerInclusive())
                || !Objects.equals(original.upperExclusive(), info.upperExclusive())) {
            throw new IllegalArgumentException("Completed chunk differs from its assigned range");
        }
        snapshots.complete(info.splitId());
        completed.put(info.splitId(), info);
    }

    public void appendPlan(MySqlSnapshotSplit split, int table, int chunk, Long lower, boolean last) {
        if (phase != MySqlHybridEnumeratorState.Phase.SNAPSHOT) throw new IllegalStateException("Snapshot discovery is inactive");
        snapshots.append(List.of(split), last);
        nextTableIndex = table;
        nextChunkIndex = chunk;
        nextChunkLower = lower;
    }
    public List<FinishedSnapshotSplitInfo> finishedSplits() { return List.copyOf(completed.values()); }
    public int nextTableIndex() { return nextTableIndex; }
    public int nextChunkIndex() { return nextChunkIndex; }
    public Long nextChunkLower() { return nextChunkLower; }
    public int pendingSnapshotCount() { return snapshots.pending().size(); }''')
edit(p, 'if (phase == MySqlHybridEnumeratorState.Phase.HANDOFF) {\n            handoffCheckpointId = checkpointId;', 'if (phase == MySqlHybridEnumeratorState.Phase.HANDOFF && handoffCheckpointId == null) {\n            handoffCheckpointId = checkpointId;')
edit(p, 'snapshots.finished());', 'snapshots.finished(), finishedSplits(), nextTableIndex, nextChunkIndex, nextChunkLower);')

p = C/'enumerator/MySqlHybridEnumeratorStateSerializer.java'
edit(p, 'private static final int VERSION = 1;', 'private static final int VERSION = 2;')
edit(p, '''            out.flush();''', '''            MySqlHybridSplitSerializer.writeFinishedSplits(out, state.finishedSplitInfos());
            out.writeInt(state.nextTableIndex());
            out.writeInt(state.nextChunkIndex());
            out.writeBoolean(state.nextChunkLower() != null);
            if (state.nextChunkLower() != null) out.writeLong(state.nextChunkLower());
            out.flush();''')
edit(p, '''            if (in.available() != 0) {''', '''            var finishedInfos = MySqlHybridSplitSerializer.readFinishedSplits(in);
            int nextTable = in.readInt();
            int nextChunk = in.readInt();
            Long nextLower = in.readBoolean() ? in.readLong() : null;
            if (in.available() != 0) {''')
edit(p, '''                    finished);''', '''                    finished, finishedInfos, nextTable, nextChunk, nextLower);''')
edit(p, 'Unsupported MySQL hybrid enumerator checkpoint', 'Unsupported MySQL hybrid enumerator checkpoint; version 1 requires explicit reinitialization')

p = C/'assigner/MySqlChunkSplitter.java'
edit(p, 'private final int chunkSize;', '''private final int chunkSize;
    private volatile PreparedStatement planningStatement;
    private volatile boolean cancelled;''')
edit(p, '    public static String splitKey(TableSchema schema) {', '''    /** Discovers exactly one next range; the caller checkpoints the returned upper boundary before continuing. */
    public MySqlSnapshotSplit planNext(TableId table, String fingerprint, int tableIndex,
            int chunkIndex, Long lower) throws SQLException {
        if (cancelled || tableIndex < 0 || chunkIndex < 0 || chunkIndex >= MAX_CHUNKS_PER_TABLE) {
            throw new SQLException("MySQL chunk discovery is cancelled or exceeds its range limit");
        }
        String key = splitKey(Objects.requireNonNull(schemas.get(table), "table schema"));
        String quoted = dialect.quoteIdentifier(key);
        String sql = "SELECT " + quoted + " FROM " + dialect.qualifiedTable(table)
                + (lower == null ? "" : " WHERE " + quoted + " >= ?")
                + " ORDER BY " + quoted + " LIMIT 1 OFFSET ?";
        try (Connection connection = connections.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            planningStatement = statement;
            statement.setQueryTimeout(60);
            if (cancelled) throw new SQLException("MySQL chunk discovery was cancelled");
            int parameter = 1;
            if (lower != null) statement.setLong(parameter++, lower);
            statement.setInt(parameter, chunkSize);
            Long upper;
            try (ResultSet rows = statement.executeQuery()) {
                upper = rows.next() ? rows.getBigDecimal(1).longValueExact() : null;
            } catch (ArithmeticException | NullPointerException invalidKey) {
                throw new SQLException("MySQL snapshot chunk key is outside the supported signed BIGINT range", invalidKey);
            }
            if (upper != null && lower != null && upper <= lower) throw new SQLException("MySQL chunk boundary did not advance");
            return new MySqlSnapshotSplit("mysql-snapshot-" + tableIndex + "-" + chunkIndex,
                    fingerprint, table, lower, upper, null);
        } finally { planningStatement = null; }
    }

    /** Signals terminal discovery cancellation without blocking the coordinator. */
    public void cancel() {
        cancelled = true;
        PreparedStatement running = planningStatement;
        if (running != null) Thread.ofVirtual().name("yak-mysql-discovery-cancel").start(() -> {
            try { running.cancel(); }
            catch (SQLException ignored) { /* Discovery owns statement cleanup and observes cancellation. */ }
        });
    }

    public static String splitKey(TableSchema schema) {''')

put(C/'events/MySqlStopSnapshotEvent.java', '''
package io.yak.ops.connector.cdc.mysql.source.events;
import io.yak.ops.core.api.connector.source.SourceEvent;
/** Releases idle snapshot I/O after the completed handoff without ending an unbounded SourceReader. */
public record MySqlStopSnapshotEvent() implements SourceEvent {}
''')
p = C/'reader/MySqlHybridSourceReader.java'
edit(p, '''        } else {
            throw new IllegalArgumentException("Unsupported MySQL hybrid Reader event");''', '''        } else if (event instanceof io.yak.ops.connector.cdc.mysql.source.events.MySqlStopSnapshotEvent) {
            splits.finishSnapshots();
        } else {
            throw new IllegalArgumentException("Unsupported MySQL hybrid Reader event");''')
p = C/'reader/MySqlHybridSplitReader.java'
edit(p, 'private boolean streaming;', 'private boolean streaming;\n    private volatile boolean snapshotsFinished;\n    private boolean snapshotsClosed;')
edit(p, 'if (binlogSplit != null && resume != null && !streaming) {', '''if (snapshotsFinished && !snapshotsClosed) {
            snapshots.close();
            snapshotsClosed = true;
        }
        if (binlogSplit != null && resume != null && !streaming) {''')
edit(p, 'public void releaseSnapshot(String splitId)', 'public void finishSnapshots() { snapshotsFinished = true; }\n    public void releaseSnapshot(String splitId)')

put(C/'enumerator/MySqlHybridSourceEnumerator.java', '''
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
public final class MySqlHybridSourceEnumerator implements SplitEnumerator<MySqlHybridSplit, MySqlHybridEnumeratorState> {
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

    public MySqlHybridSourceEnumerator(SplitEnumeratorContext<MySqlHybridSplit> context,
            MySqlCdcSourceConfig config, String fingerprint, int chunkSize, MySqlHybridEnumeratorState restored) {
        this(context, config, fingerprint, chunkSize, restored, new TableMetrics());
    }

    public MySqlHybridSourceEnumerator(SplitEnumeratorContext<MySqlHybridSplit> context,
            MySqlCdcSourceConfig config, String fingerprint, int chunkSize,
            MySqlHybridEnumeratorState restored, TableMetrics metrics) {
        this.context = Objects.requireNonNull(context, "context");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        tables = List.copyOf(config.schemas().keySet());
        tables.forEach(metrics::register);
        generation = metrics.beginSnapshot(restored != null);
        splitter = new MySqlChunkSplitter(config, chunkSize);
        assigner = new MySqlHybridSplitAssigner(fingerprint, restored);
        restoredHandoff = restored != null && restored.phase() == MySqlHybridEnumeratorState.Phase.HANDOFF;
        if (context.currentParallelism() < 1 || context.currentParallelism() > 16
                || assigner.nextTableIndex() > tables.size()) {
            throw new IllegalArgumentException("Invalid MySQL Hybrid parallelism or discovery position");
        }
        if (restored != null) {
            boolean complete = restoredHandoff || restored.phase() == MySqlHybridEnumeratorState.Phase.STREAMING;
            metrics.phase(generation, complete ? TableMetrics.Phase.HANDOFF
                    : restored.phase() == MySqlHybridEnumeratorState.Phase.SNAPSHOT
                            ? TableMetrics.Phase.SNAPSHOT : TableMetrics.Phase.INITIALIZING, complete);
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

    @Override public void start() {
        if (started || closed) throw new IllegalStateException("MySQL Hybrid enumerator cannot start twice");
        started = true;
        if (restoredHandoff) {
            assigner.resumeRestoredHandoff();
            sendResumeEvents();
        } else if (assigner.snapshotComplete()) enterHandoff();
        else planSnapshotAsync();
    }

    @Override public void handleSplitRequest(int reader) {
        if (closed) return;
        if (!started || !context.registeredReaders().contains(reader)) throw new IllegalStateException("Unregistered Hybrid reader");
        waitingReaders.add(reader);
        assignAvailable();
        planSnapshotAsync();
    }

    @Override public void addReader(int reader) {
        if (closed) return;
        if (assigner.phase() == MySqlHybridEnumeratorState.Phase.STREAMING) sendResumeEvent(reader);
        assignAvailable();
    }

    @Override public void handleSourceEvent(int reader, SourceEvent event) {
        if (closed) return;
        if (event instanceof MySqlLowWatermarkEvent low) {
            if (reader != 0) throw new IllegalArgumentException("Only the Binlog owner may report bootstrap");
            assigner.captureLow(low.offset());
            metrics.phase(generation, TableMetrics.Phase.SNAPSHOT, false);
            planSnapshotAsync();
        } else if (event instanceof MySqlSnapshotFinishedEvent finished) {
            assigner.finishSnapshot(finished.finished());
            metrics.splitFinished(generation, finished.splitId(), finished.finished().tableId());
            context.sendEventToSourceReader(reader, new MySqlSnapshotFinishedAckEvent(finished.splitId()));
            if (assigner.snapshotComplete()) enterHandoff();
            assignAvailable();
            planSnapshotAsync();
        } else throw new IllegalArgumentException("Unsupported MySQL Hybrid source event");
    }

    @Override public void addSplitsBack(List<MySqlHybridSplit> splits, int reader) {
        if (closed || reader < 0 || reader >= context.currentParallelism()) throw new IllegalStateException("Invalid split return");
        for (MySqlHybridSplit split : splits) {
            if (split instanceof MySqlSnapshotSplit snapshot) assigner.addBack(snapshot);
            else if (split instanceof MySqlHybridBinlogSplit binlog) {
                if (reader != 0) throw new IllegalArgumentException("Binlog belongs to reader zero");
                assigner.addBack(binlog);
            }
        }
        assignAvailable();
    }

    @Override public MySqlHybridEnumeratorState snapshotState(long checkpointId) {
        if (!started || closed) throw new IllegalStateException("Inactive MySQL Hybrid enumerator");
        return assigner.snapshot(checkpointId);
    }

    @Override public void notifyCheckpointComplete(long checkpointId) {
        boolean handoff = assigner.completeCheckpoint(checkpointId);
        metrics.checkpointCompleted(generation, checkpointId, handoff);
        if (handoff) sendResumeEvents();
    }

    @Override public void close() {
        closed = true;
        splitter.cancel();
        metrics.sourceClosed(generation);
        waitingReaders.clear();
    }

    private void planSnapshotAsync() {
        if (!started || closed || planning || assigner.snapshotPlanned()
                || assigner.phase() != MySqlHybridEnumeratorState.Phase.SNAPSHOT
                || assigner.pendingSnapshotCount() >= MAX_PENDING_CHUNKS) return;
        int tableIndex = assigner.nextTableIndex();
        int chunkIndex = assigner.nextChunkIndex();
        Long lower = assigner.nextChunkLower();
        if (tableIndex >= tables.size()) throw new IllegalStateException("Incomplete discovery terminal marker");
        TableId table = tables.get(tableIndex);
        planning = true;
        context.callAsync(() -> splitter.planNext(table, fingerprint, tableIndex, chunkIndex, lower), (split, failure) -> {
            planning = false;
            if (closed) return;
            if (failure != null) {
                metrics.readFailed(generation, table, false);
                throw new IllegalStateException("MySQL snapshot chunk discovery failed", failure);
            }
            boolean tableComplete = split.upperExclusive() == null;
            int nextTable = tableComplete ? tableIndex + 1 : tableIndex;
            assigner.appendPlan(split, nextTable, tableComplete ? 0 : chunkIndex + 1,
                    split.upperExclusive(), nextTable == tables.size());
            // Until discovery ends for a table, its denominator remains unknown rather than reporting false completion.
            if (tableComplete) metrics.planned(generation, table, chunkIndex + 1);
            assignAvailable();
            planSnapshotAsync();
        });
    }

    private void enterHandoff() {
        var high = assigner.finishedSplits().stream().map(FinishedSnapshotSplitInfo::highWatermark)
                .max(BinlogOffsetOrder::compare).orElseThrow();
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
            if (!context.registeredReaders().contains(reader)) { waitingReaders.remove(reader); continue; }
            MySqlSnapshotSplit split = assigner.nextSnapshot();
            if (split == null) break;
            context.assignSplit(split, reader);
            waitingReaders.remove(reader);
        }
    }

    private void sendResumeEvents() { context.registeredReaders().forEach(this::sendResumeEvent); }
    private void sendResumeEvent(int reader) {
        context.sendEventToSourceReader(reader, reader == 0
                ? new MySqlResumeBinlogEvent(assigner.high(), assigner.finishedSplits()) : new MySqlStopSnapshotEvent());
    }
}
''')

# Existing transition tests now assert that streaming begins at the minimum completed chunk High.
for name in ['MySqlHybridBinlogSplitTest.java', 'MySqlHybridSplitSerializerTest.java']:
    p = T/'split'/name
    edit(p, '.startStreaming(high)', '.startStreaming(high, completed(high))')
    text = p.read_text()
    text = text.replace('.startStreaming(offset(100L))', '.startStreaming(offset(100L), completed(offset(100L)))')
    text = text.replace('assertSame(paused.binlog(), running.binlog());', 'assertNotSame(paused.binlog(), running.binlog());')
    text = text.replace('assertEquals(low, running.binlog().offset());', 'assertEquals(high, running.binlog().offset());')
    text = text.replace('.withEmittedOffset(low, new byte[] {1, 2, 3})', '.withEmittedOffset(high, new byte[] {1, 2, 3})')
    text = text.replace('assertEquals(low, restored.binlog().offset());', 'assertEquals(high, restored.binlog().offset());')
    text = text.replace('codec', 'serializer').replace('without changing PR1 serializer state', 'with complete normalized snapshot metadata')
    text = text.replace('serializer.deserialize(1, new byte[]', 'serializer.deserialize(serializer.getVersion(), new byte[]')
    helper = '''
    private static java.util.List<FinishedSnapshotSplitInfo> completed(BinlogOffset high) {
        return java.util.List.of(new FinishedSnapshotSplitInfo(
                new io.yak.ops.core.data.TableId("shop", null, "orders"), "chunk", null, null, high));
    }
'''
    text = text.rsplit('}',1)[0] + helper + '}\n'
    p.write_text(text)
p = T/'enumerator/MySqlHybridHandoffTest.java'
s = p.read_text()
s = s.replace('assigner.finishSnapshot(split.splitId());', 'assigner.finishSnapshot(finished(split, 300));',1)
s = s.replace('assigner.finishSnapshot(split.splitId());', 'assigner.finishSnapshot(finished(split, 200));')
s = s.replace('checkpointDuringHighWatermarkLookupAllowsSafeRetryOnRestore', 'restoresCompletedWatermarksBeforeHandoff')
s = s.replace('assertTrue(assigner.completeCheckpoint(12));', 'assigner.snapshot(13);\n        assertTrue(assigner.completeCheckpoint(12));')
s = s.rsplit('}',1)[0] + '''
    private static io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo finished(
            MySqlSnapshotSplit split, long position) {
        return new io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo(split.tableId(),
                split.splitId(), split.lowerInclusive(), split.upperExclusive(),
                new BinlogOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", position)));
    }
}
'''
p.write_text(s)
