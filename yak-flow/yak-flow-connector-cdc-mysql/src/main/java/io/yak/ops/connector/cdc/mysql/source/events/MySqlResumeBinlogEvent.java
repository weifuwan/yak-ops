package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.core.api.connector.source.SourceEvent;
import java.util.Objects;

/**
 * Opens the paused Binlog gate after a completed Checkpoint covers every snapshot split.
 *
 * <p>The high watermark marks the end of the replay/backfill window, not a request to
 * skip records between the low and high watermarks.
 */
public record MySqlResumeBinlogEvent(BinlogOffset highWatermark) implements SourceEvent {

    public MySqlResumeBinlogEvent {
        Objects.requireNonNull(highWatermark, "highWatermark");
    }
}
