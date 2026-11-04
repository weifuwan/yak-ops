package org.apache.flink.cdc.connectors.mysql.source.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.mysql.source.enumerator.MySqlSourceEnumerator;
import org.apache.flink.cdc.connectors.mysql.source.offset.BinlogOffset;
import org.apache.flink.cdc.connectors.mysql.source.reader.MySqlSourceReader;

import java.util.Map;

/**
 * The {@link SourceEvent} that {@link MySqlSourceReader} sends to {@link MySqlSourceEnumerator} to
 * notify the snapshot split has read finished with the consistent binlog position.
 */
public class FinishedSnapshotSplitsReportEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    private final Map<String, BinlogOffset> finishedOffsets;

    public FinishedSnapshotSplitsReportEvent(Map<String, BinlogOffset> finishedOffsets) {
        this.finishedOffsets = finishedOffsets;
    }

    public Map<String, BinlogOffset> getFinishedOffsets() {
        return finishedOffsets;
    }

    @Override
    public String toString() {
        return "FinishedSnapshotSplitsReportEvent{" + "finishedOffsets=" + finishedOffsets + '}';
    }
}
