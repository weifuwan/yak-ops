package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.core.api.connector.source.SourceEvent;
import java.util.Objects;

/** Reports the normalized chunk's range and High only after all its output has been emitted. */
public record MySqlSnapshotFinishedEvent(FinishedSnapshotSplitInfo finished) implements SourceEvent {
    public MySqlSnapshotFinishedEvent {
        Objects.requireNonNull(finished, "finished");
    }

    public String splitId() {
        return finished.splitId();
    }

    @Override
    public String toString() {
        return "MySqlSnapshotFinishedEvent{split=" + splitId() + ", checkpoint=redacted}";
    }
}
