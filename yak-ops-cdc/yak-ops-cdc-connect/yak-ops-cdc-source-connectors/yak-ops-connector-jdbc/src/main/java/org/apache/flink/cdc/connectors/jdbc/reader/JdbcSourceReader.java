package org.apache.flink.cdc.connectors.jdbc.reader;

import org.apache.flink.api.connector.source.SourceReaderContext;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSourceConfig;
import org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit;
import org.apache.flink.connector.base.source.reader.SingleThreadMultiplexSourceReaderBase;

import java.util.Map;

/** Flink source reader that delegates JDBC I/O to the connector-base split fetcher. */
public final class JdbcSourceReader
        extends SingleThreadMultiplexSourceReaderBase<
                JdbcRecord, Event, JdbcSourceSplit, JdbcSourceSplitState> {

    public JdbcSourceReader(JdbcSourceConfig config, SourceReaderContext context) {
        super(
                () -> new JdbcSplitReader(config),
                new JdbcRecordEmitter(),
                context.getConfiguration(),
                context);
    }

    @Override
    protected void onSplitFinished(Map<String, JdbcSourceSplitState> finishedSplitIds) {}

    @Override
    protected JdbcSourceSplitState initializedState(JdbcSourceSplit split) {
        return new JdbcSourceSplitState(split);
    }

    @Override
    protected JdbcSourceSplit toSplitType(String splitId, JdbcSourceSplitState splitState) {
        return splitState.toSourceSplit();
    }
}
