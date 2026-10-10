package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.source.reader.RecordsBySplits;
import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.debezium.MySqlBinlogEngine;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Multiplexes JDBC Snapshot scans and a paused Debezium Binlog on one Connector Base fetcher.
 *
 * <p>One Debezium engine is started before snapshot planning and is kept behind a bounded
 * handover queue. The first event anchors the low watermark and remains represented by
 * the subsequent snapshot; later Binlog events cannot leave this Reader until the source
 * coordinator confirms a checkpoint covering all Snapshot splits.
 */
public final class MySqlHybridSplitReader implements SplitReader<MySqlHybridFetchedRecord, MySqlHybridSplit> {

    private final MySqlSnapshotSplitReader snapshots;
    private final MySqlBinlogEngine binlog;
    private final Deque<MySqlSnapshotSplit> pending = new ArrayDeque<>();

    private MySqlSnapshotSplit activeSnapshot;
    private BinlogEvent bootstrapCandidate;
    private boolean binlogAssigned;
    private boolean bootstrap;
    private volatile boolean streaming;
    private volatile boolean resumeRequested;
    private boolean closed;

    public MySqlHybridSplitReader(MySqlCdcSourceConfig config) {
        snapshots = new MySqlSnapshotSplitReader(Objects.requireNonNull(config, "config"));
        binlog = new MySqlBinlogEngine(config);
    }

    @Override
    public void addSplits(List<MySqlHybridSplit> splits) throws Exception {
        for (MySqlHybridSplit split : splits) {
            if (split instanceof MySqlSnapshotSplit snapshot) {
                pending.addLast(snapshot);
            } else if (split instanceof MySqlHybridBinlogSplit stream) {
                if (binlogAssigned) {
                    throw new IllegalStateException("A Hybrid Reader cannot own two Binlog splits");
                }
                bootstrap = stream.phase() == MySqlHybridBinlogSplit.Phase.BOOTSTRAP;
                streaming = stream.phase() == MySqlHybridBinlogSplit.Phase.STREAMING || resumeRequested;
                binlog.start(stream.binlog());
                binlogAssigned = true;
            } else {
                throw new IllegalArgumentException("Unknown MySQL Hybrid Split");
            }
        }
    }

    @Override
    public RecordsWithSplitIds<MySqlHybridFetchedRecord> fetch() throws Exception {
        if (closed) {
            throw new IllegalStateException("MySQL hybrid reader has closed");
        }
        if (bootstrap && binlogAssigned) {
            if (bootstrapCandidate == null) {
                bootstrapCandidate = binlog.poll();
            }
            if (bootstrapCandidate != null) {
                byte[] history = binlog.snapshotHistory();
                if (history.length == 0) {
                    // Debezium schema discovery and the first heartbeat are asynchronous.
                    // Keep the anchor event until its corresponding history is checkpointable.
                    Thread.sleep(100);
                    return new RecordsBySplits<>(Map.of(), Set.of());
                }
                BinlogEvent first = bootstrapCandidate;
                bootstrapCandidate = null;
                bootstrap = false;
                return new RecordsBySplits<>(
                        Map.of(
                                MySqlHybridBinlogSplit.ID,
                                List.of(new MySqlHybridFetchedRecord(List.of(), null, first.offset(), true))),
                        Set.of());
            }
        }
        if (activeSnapshot == null && !pending.isEmpty()) {
            activeSnapshot = pending.removeFirst();
            snapshots.open(activeSnapshot);
        }
        if (activeSnapshot != null) {
            RecordsWithSplitIds<MySqlHybridFetchedRecord> batch = snapshots.fetch();
            if (batch.finishedSplits().contains(activeSnapshot.splitId())) {
                activeSnapshot = null;
            }
            return batch;
        }
        if (binlogAssigned && streaming) {
            BinlogEvent event = binlog.poll();
            if (event != null) {
                return new RecordsBySplits<>(
                        Map.of(
                                MySqlHybridBinlogSplit.ID,
                                List.of(new MySqlHybridFetchedRecord(event.records(), null, event.offset(), false))),
                        Set.of());
            }
        } else {
            Thread.sleep(100);
        }
        return new RecordsBySplits<>(Map.of(), Set.of());
    }

    /** Releases the Binlog gate only after the hybrid checkpoint handoff. */
    public void resumeBinlog(BinlogOffset high) {
        Objects.requireNonNull(high, "high");
        resumeRequested = true;
        streaming = true;
    }

    public byte[] snapshotHistory() {
        try {
            return binlog.snapshotHistory();
        } catch (IOException problem) {
            throw new IllegalStateException("MySQL hybrid Schema History checkpoint failed", problem);
        }
    }

    @Override
    public void wakeUp() {
        // Blocking fetch calls are bounded; ordinary snapshot assignment must not stop Binlog I/O.
    }

    @Override
    public void cancel() {
        binlog.requestStop();
    }

    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        Exception failure = null;
        try {
            snapshots.close();
        } catch (Exception problem) {
            failure = problem;
        }
        try {
            binlog.close();
        } catch (Exception problem) {
            if (failure == null) {
                failure = problem;
            } else {
                failure.addSuppressed(problem);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
