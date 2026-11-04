package org.apache.flink.cdc.connectors.base.source.meta.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.base.source.enumerator.IncrementalSourceEnumerator;
import org.apache.flink.cdc.connectors.base.source.reader.IncrementalSourceReader;

import java.util.List;

/**
 * The {@link SourceEvent} that {@link IncrementalSourceEnumerator} sends to {@link
 * IncrementalSourceReader} to notify the finished snapshot splits has been received, i.e.
 * acknowledge for {@link FinishedSnapshotSplitsReportEvent}.
 */
public class FinishedSnapshotSplitsAckEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    private final List<String> finishedSplits;

    public FinishedSnapshotSplitsAckEvent(List<String> finishedSplits) {
        this.finishedSplits = finishedSplits;
    }

    public List<String> getFinishedSplits() {
        return finishedSplits;
    }

    @Override
    public String toString() {
        return "FinishedSnapshotSplitsAckEvent{" + "finishedSplits=" + finishedSplits + '}';
    }
}
