package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.source.reader.SingleThreadMultiplexSourceReaderBase;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlSnapshotFinishedAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlSnapshotFinishedEvent;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mailbox-owned SourceReader for parallel snapshot ranges and one deferred Binlog stream.
 *
 * <p>Finished but not acknowledged snapshot splits remain in Reader checkpoint state until
 * the Enumerator confirms receipt of their completion. The Binlog gate opens only through
 * a coordinator event after the snapshot handoff checkpoint completes.
 */
public final class MySqlHybridSourceReader
        extends SingleThreadMultiplexSourceReaderBase<
                MySqlHybridFetchedRecord, TableRecord, MySqlHybridSplit, MySqlHybridSplitState> {

    private final MySqlHybridSplitReader splits;
    private final TableMetrics metrics;
    private final long generation;
    private final Map<String, MySqlSnapshotSplit> finishedUnacked = new LinkedHashMap<>();
    private MySqlResumeBinlogEvent resumeEvent;
    private final java.util.Set<String> acknowledged = new java.util.HashSet<>();
    private final Map<String, Long> finishedCheckpoint = new LinkedHashMap<>();

    public MySqlHybridSourceReader(MySqlCdcSourceConfig config, SourceReaderContext context) {
        this(config, context, new TableMetrics());
    }

    /** Creates a Reader bound to the generation opened by its source coordinator. */
    public MySqlHybridSourceReader(MySqlCdcSourceConfig config, SourceReaderContext context, TableMetrics metrics) {
        this(new MySqlHybridSplitReader(config, metrics, context.getIndexOfSubtask()), context, metrics);
    }

    private MySqlHybridSourceReader(MySqlHybridSplitReader splits, SourceReaderContext context, TableMetrics metrics) {
        super(() -> splits, new MySqlHybridRecordEmitter(context, splits, metrics), context);
        this.metrics = metrics;
        generation = metrics.generation();
        this.splits = splits;
    }

    @Override
    public void start() throws Exception {
        context.sendSplitRequest();
    }

    @Override
    protected MySqlHybridSplitState initializedState(MySqlHybridSplit split) {
        MySqlHybridSplitState state = new MySqlHybridSplitState(split);
        if (resumeEvent != null && split instanceof MySqlHybridBinlogSplit) {
            state.resume(resumeEvent.highWatermark(), resumeEvent.finishedSplits());
        }
        return state;
    }

    @Override
    protected MySqlHybridSplit toSplitType(String splitId, MySqlHybridSplitState state) {
        return state.checkpoint(state.binlog() ? splits.snapshotHistory() : null);
    }

    @Override
    protected void onSplitFinished(Map<String, MySqlHybridSplitState> finished) {
        for (var entry : finished.entrySet()) {
            MySqlHybridSplit split = entry.getValue().checkpoint(null);
            if (!(split instanceof MySqlSnapshotSplit snapshot)) {
                throw new IllegalStateException("Continuous MySQL Binlog split cannot finish");
            }
            finishedUnacked.put(snapshot.splitId(), snapshot);
            context.sendSourceEventToCoordinator(new MySqlSnapshotFinishedEvent(
                    Objects.requireNonNull(snapshot.finished(), "Finished chunk has no watermark")));
            context.sendSplitRequest();
        }
    }

    @Override
    public List<MySqlHybridSplit> snapshotState(long checkpointId) {
        forEachActiveSplitState(state -> state.snapshotCheckpoint(checkpointId));
        acknowledged.forEach(id -> finishedCheckpoint.putIfAbsent(id, checkpointId));
        List<MySqlHybridSplit> active = new ArrayList<>(super.snapshotState(checkpointId));
        active.addAll(finishedUnacked.values());
        return List.copyOf(active);
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        forEachActiveSplitState(state -> {
            String id = state.completeCheckpoint(checkpointId);
            if (id != null) splits.releaseSnapshot(id);
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
        if (event instanceof MySqlSnapshotFinishedAckEvent ack) {
            if (finishedUnacked.containsKey(ack.splitId())) acknowledged.add(ack.splitId());
        } else if (event instanceof MySqlResumeBinlogEvent resume) {
            resumeEvent = resume;
            forEachActiveSplitState(state -> {
                if (state.binlog()) {
                    state.resume(resume.highWatermark(), resume.finishedSplits());
                }
            });
            metrics.streaming(generation);
            splits.resumeBinlog(resume);
        } else if (event instanceof io.yak.ops.connector.cdc.mysql.source.events.MySqlStopSnapshotEvent) {
            splits.finishSnapshots();
        } else {
            throw new IllegalArgumentException("Unsupported MySQL hybrid Reader event");
        }
    }
}
