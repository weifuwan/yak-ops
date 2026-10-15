package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.core.api.connector.source.SourceEvent;
import java.util.List;
import java.util.Objects;

/** Releases the checkpoint-protected Binlog handoff with its complete per-chunk filter metadata. */
public record MySqlResumeBinlogEvent(BinlogOffset highWatermark, List<FinishedSnapshotSplitInfo> finishedSplits)
        implements SourceEvent {
    public MySqlResumeBinlogEvent {
        Objects.requireNonNull(highWatermark, "highWatermark");
        finishedSplits = List.copyOf(Objects.requireNonNull(finishedSplits, "finishedSplits"));
        if (finishedSplits.isEmpty())
            throw new IllegalArgumentException("Binlog handoff requires finished chunk metadata");
    }

    @Override
    public String toString() {
        return "MySqlResumeBinlogEvent{checkpoint=redacted}";
    }
}
