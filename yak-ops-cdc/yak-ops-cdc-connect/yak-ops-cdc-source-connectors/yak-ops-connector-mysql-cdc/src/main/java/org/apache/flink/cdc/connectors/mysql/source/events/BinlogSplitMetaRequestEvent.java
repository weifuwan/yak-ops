package org.apache.flink.cdc.connectors.mysql.source.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.mysql.source.enumerator.MySqlSourceEnumerator;
import org.apache.flink.cdc.connectors.mysql.source.reader.MySqlSourceReader;

/**
 * The {@link SourceEvent} that {@link MySqlSourceReader} sends to {@link MySqlSourceEnumerator} to
 * pull binlog metadata, i.e. sending {@link BinlogSplitMetaEvent}.
 */
public class BinlogSplitMetaRequestEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    private final String splitId;
    private final int requestMetaGroupId;

    private final int totalFinishedSplitSize;

    public BinlogSplitMetaRequestEvent(
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
