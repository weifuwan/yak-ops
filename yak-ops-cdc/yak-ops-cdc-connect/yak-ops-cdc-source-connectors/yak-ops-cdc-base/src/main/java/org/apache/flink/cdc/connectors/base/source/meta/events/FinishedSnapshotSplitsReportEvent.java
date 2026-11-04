package org.apache.flink.cdc.connectors.base.source.meta.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.base.source.enumerator.IncrementalSourceEnumerator;
import org.apache.flink.cdc.connectors.base.source.meta.offset.Offset;
import org.apache.flink.cdc.connectors.base.source.reader.IncrementalSourceReader;

import java.util.Map;

/**
 * The {@link SourceEvent} that {@link IncrementalSourceReader} sends to {@link
 * IncrementalSourceEnumerator} to notify the snapshot split has read finished with the consistent
 * change log position.
 */
public class FinishedSnapshotSplitsReportEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    private final Map<String, Offset> finishedOffsets;

    public FinishedSnapshotSplitsReportEvent(Map<String, Offset> finishedOffsets) {
        this.finishedOffsets = finishedOffsets;
    }

    public Map<String, Offset> getFinishedOffsets() {
        return finishedOffsets;
    }

    @Override
    public String toString() {
        return "FinishedSnapshotSplitsReportEvent{" + "finishedOffsets=" + finishedOffsets + '}';
    }
}
