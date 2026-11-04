package org.apache.flink.cdc.connectors.mysql.source.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.mysql.source.enumerator.MySqlSourceEnumerator;
import org.apache.flink.cdc.connectors.mysql.source.reader.MySqlSourceReader;

/**
 * The {@link SourceEvent} that {@link MySqlSourceEnumerator} sends to {@link MySqlSourceReader} to
 * pass the latest finished snapshot splits number.
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
