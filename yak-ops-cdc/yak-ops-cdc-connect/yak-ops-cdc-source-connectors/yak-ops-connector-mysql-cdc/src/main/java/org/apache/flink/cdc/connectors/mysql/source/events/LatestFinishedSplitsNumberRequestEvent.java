package org.apache.flink.cdc.connectors.mysql.source.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.mysql.source.enumerator.MySqlSourceEnumerator;
import org.apache.flink.cdc.connectors.mysql.source.reader.MySqlSourceReader;

/**
 * The {@link SourceEvent} that {@link MySqlSourceReader} sends to {@link MySqlSourceEnumerator} to
 * ask the latest finished snapshot splits number.
 */
public class LatestFinishedSplitsNumberRequestEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    public LatestFinishedSplitsNumberRequestEvent() {}
}
