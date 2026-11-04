package org.apache.flink.cdc.connectors.base.source.meta.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.base.source.enumerator.IncrementalSourceEnumerator;
import org.apache.flink.cdc.connectors.base.source.reader.IncrementalSourceReader;

/**
 * The {@link SourceEvent} that {@link IncrementalSourceEnumerator} sends to {@link
 * IncrementalSourceReader} to pass the latest finished snapshot splits number.
 */
public class LatestFinishedSplitsNumberEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;
    private final int latestFinishedSplitsNumber;

    public LatestFinishedSplitsNumberEvent(int latestFinishedSplitsNumber) {
        this.latestFinishedSplitsNumber = latestFinishedSplitsNumber;
    }

    public int getLatestFinishedSplitsNumber() {
        return latestFinishedSplitsNumber;
    }

    @Override
    public String toString() {
        return "LatestFinishedSplitsNumberEvent{"
                + "latestFinishedSplitsNumber="
                + latestFinishedSplitsNumber
                + '}';
    }
}
