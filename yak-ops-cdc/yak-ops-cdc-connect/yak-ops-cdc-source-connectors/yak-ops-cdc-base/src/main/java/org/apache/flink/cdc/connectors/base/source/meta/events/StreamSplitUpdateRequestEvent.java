package org.apache.flink.cdc.connectors.base.source.meta.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.base.source.enumerator.IncrementalSourceEnumerator;
import org.apache.flink.cdc.connectors.base.source.reader.IncrementalSourceSplitReader;

/**
 * The {@link SourceEvent} that {@link IncrementalSourceEnumerator} broadcasts to {@link
 * IncrementalSourceSplitReader} to tell the source reader to update the stream split after newly
 * added table snapshot splits finished.
 */
public class StreamSplitUpdateRequestEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    public StreamSplitUpdateRequestEvent() {}
}
