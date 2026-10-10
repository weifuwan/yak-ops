package io.yak.ops.connector.cdc.mysql.source.reader;

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

/**
 * Mailbox-owned SourceReader for parallel snapshot ranges and one deferred Binlog stream.
 *
 * <p>Finished but not acknowledged snapshot splits remain in Reader checkpoint state until
 * the Enumerator confirms receipt of their completion. The Binlog gate opens only through
 * a coordinator event after the snapshot handoff checkpoint completes.
 */
public final class MySqlHybridSourceReader extends SingleThreadMultiplexSourceReaderBase<
        MySqlHybridFetchedRecord, TableRecord, MySqlHybridSplit, MySqlHybridSplitState> {

    private final MySqlHybridSplitReader splits;
    private final Map<String, MySqlSnapshotSplit> finishedUnacked = new LinkedHashMap<>();
    private io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset resumeHigh;

    public MySqlHybridSourceReader(MySqlCdcSourceConfig config, SourceReaderContext context) {
        this(new MySqlHybridSplitReader(config), context);
    }

    private MySqlHybridSourceReader(MySqlHybridSplitReader splits, SourceReaderContext context) {
        super(() -> splits, new MySqlHybridRecordEmitter(context, splits), context);
        this.splits = splits;
    }

    @Override
    public void start() throws Exception {
        context.sendSplitRequest();
    }

    @Override
    protected MySqlHybridSplitState initializedState(MySqlHybridSplit split) {
        MySqlHybridSplitState state = new MySqlHybridSplitState(split);
        if (resumeHigh != null && split instanceof MySqlHybridBinlogSplit) {
            state.resume(resumeHigh);
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
            context.sendSourceEventToCoordinator(new MySqlSnapshotFinishedEvent(snapshot.splitId()));
            context.sendSplitRequest();
        }
    }

    @Override
    public List<MySqlHybridSplit> snapshotState(long checkpointId) {
        List<MySqlHybridSplit> active = new ArrayList<>(super.snapshotState(checkpointId));
        active.addAll(finishedUnacked.values());
        return List.copyOf(active);
    }

    @Override
    public void handleSourceEvents(SourceEvent event) {
        if (event instanceof MySqlSnapshotFinishedAckEvent ack) {
            finishedUnacked.remove(ack.splitId());
        } else if (event instanceof MySqlResumeBinlogEvent resume) {
            resumeHigh = resume.highWatermark();
            forEachActiveSplitState(state -> {
                if (state.binlog()) {
                    state.resume(resumeHigh);
                }
            });
            splits.resumeBinlog(resumeHigh);
        } else {
            throw new IllegalArgumentException("Unsupported MySQL hybrid Reader event");
        }
    }
}
