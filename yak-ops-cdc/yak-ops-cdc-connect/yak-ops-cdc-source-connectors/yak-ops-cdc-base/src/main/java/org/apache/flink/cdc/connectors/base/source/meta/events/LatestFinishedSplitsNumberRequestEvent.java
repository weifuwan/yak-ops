package org.apache.flink.cdc.connectors.base.source.meta.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.base.source.enumerator.IncrementalSourceEnumerator;
import org.apache.flink.cdc.connectors.base.source.reader.IncrementalSourceReader;

/**
 * The {@link SourceEvent} that {@link IncrementalSourceReader} sends to {@link
 * IncrementalSourceEnumerator} to ask the latest finished snapshot splits number.
 */
public class LatestFinishedSplitsNumberRequestEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    public LatestFinishedSplitsNumberRequestEvent() {}
}
