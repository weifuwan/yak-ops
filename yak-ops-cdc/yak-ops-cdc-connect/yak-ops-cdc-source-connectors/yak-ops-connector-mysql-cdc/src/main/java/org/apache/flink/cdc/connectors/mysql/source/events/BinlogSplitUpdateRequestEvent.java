package org.apache.flink.cdc.connectors.mysql.source.events;

import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.cdc.connectors.mysql.source.enumerator.MySqlSourceEnumerator;
import org.apache.flink.cdc.connectors.mysql.source.reader.MySqlSourceReader;

/**
 * The {@link SourceEvent} that {@link MySqlSourceEnumerator} broadcasts to {@link
 * MySqlSourceReader} to tell the source reader to update the binlog split after newly added table
 * snapshot splits finished.
 */
public class BinlogSplitUpdateRequestEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    public BinlogSplitUpdateRequestEvent() {}
}
