package org.apache.flink.cdc.connectors.mysql.source.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.mysql.source.enumerator.MySqlSourceEnumerator;
import org.apache.flink.cdc.connectors.mysql.source.reader.MySqlSourceReader;

/**
 * The {@link SourceEvent} that {@link MySqlSourceEnumerator} sends to {@link MySqlSourceReader} to
 * notify reader should report its finished snapshot splits, i.e. sending {@link
 * FinishedSnapshotSplitsReportEvent}.
 */
public class FinishedSnapshotSplitsRequestEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    public FinishedSnapshotSplitsRequestEvent() {}
}
