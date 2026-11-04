package org.apache.flink.cdc.connectors.base.source.meta.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.base.source.enumerator.IncrementalSourceEnumerator;
import org.apache.flink.cdc.connectors.base.source.reader.IncrementalSourceReader;

/**
 * The {@link SourceEvent} that {@link IncrementalSourceEnumerator} sends to {@link
 * IncrementalSourceReader} to notify reader should report its finished snapshot splits, i.e.
 * sending {@link FinishedSnapshotSplitsReportEvent}.
 */
public class FinishedSnapshotSplitsRequestEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    public FinishedSnapshotSplitsRequestEvent() {}
}
