package org.apache.flink.cdc.connectors.base.source.meta.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.base.source.enumerator.IncrementalSourceEnumerator;
import org.apache.flink.cdc.connectors.base.source.reader.IncrementalSourceReader;

/**
 * The {@link SourceEvent} that {@link IncrementalSourceReader} sends to {@link
 * IncrementalSourceEnumerator} to pull change log metadata, i.e. sending {@link
 * StreamSplitMetaEvent}.
 */
public class StreamSplitMetaRequestEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    private final String splitId;
    private final int requestMetaGroupId;

    private final int totalFinishedSplitSize;

    public StreamSplitMetaRequestEvent(
            String splitId, int requestMetaGroupId, int totalFinishedSplitSize) {
        this.splitId = splitId;
        this.requestMetaGroupId = requestMetaGroupId;
        this.totalFinishedSplitSize = totalFinishedSplitSize;
    }

    public String getSplitId() {
        return splitId;
    }

    public int getRequestMetaGroupId() {
        return requestMetaGroupId;
    }

    public int getTotalFinishedSplitSize() {
        return totalFinishedSplitSize;
    }
}
