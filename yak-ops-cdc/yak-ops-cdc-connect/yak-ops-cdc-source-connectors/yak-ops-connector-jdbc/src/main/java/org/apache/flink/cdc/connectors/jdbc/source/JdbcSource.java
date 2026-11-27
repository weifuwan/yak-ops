package org.apache.flink.cdc.connectors.jdbc.source;

import org.apache.flink.api.connector.source.Boundedness;
import org.apache.flink.api.connector.source.Source;
import org.apache.flink.api.connector.source.SourceReader;
import org.apache.flink.api.connector.source.SourceReaderContext;
import org.apache.flink.api.connector.source.SplitEnumerator;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSourceConfig;
import org.apache.flink.cdc.connectors.jdbc.enumerator.JdbcEnumeratorStateSerializer;
import org.apache.flink.cdc.connectors.jdbc.enumerator.JdbcSplitEnumerator;
import org.apache.flink.cdc.connectors.jdbc.reader.JdbcSourceReader;
import org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplitSerializer;
import org.apache.flink.core.io.SimpleVersionedSerializer;

/** Bounded Flink Source for reading database tables through JDBC. */
public final class JdbcSource
        implements Source<
                Event,
                org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit,
                org.apache.flink.cdc.connectors.jdbc.enumerator.JdbcEnumeratorState> {

    private static final long serialVersionUID = 1L;

    private final JdbcSourceConfig config;

    public JdbcSource(JdbcSourceConfig config) {
        this.config = config;
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.BOUNDED;
    }

    @Override
    public SourceReader<
                    Event, org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit>
            createReader(SourceReaderContext readerContext) {
        return new JdbcSourceReader(config, readerContext);
    }

    @Override
    public SplitEnumerator<
                    org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit,
                    org.apache.flink.cdc.connectors.jdbc.enumerator.JdbcEnumeratorState>
            createEnumerator(
                    SplitEnumeratorContext<
                                    org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit>
                            enumContext) {
        return new JdbcSplitEnumerator(enumContext, config, null);
    }

    @Override
    public SplitEnumerator<
                    org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit,
                    org.apache.flink.cdc.connectors.jdbc.enumerator.JdbcEnumeratorState>
            restoreEnumerator(
                    SplitEnumeratorContext<
                                    org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit>
                            enumContext,
                    org.apache.flink.cdc.connectors.jdbc.enumerator.JdbcEnumeratorState checkpoint) {
        return new JdbcSplitEnumerator(enumContext, config, checkpoint);
    }

    @Override
    public SimpleVersionedSerializer<
            org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit>
            getSplitSerializer() {
        return new JdbcSourceSplitSerializer();
    }

    @Override
    public SimpleVersionedSerializer<
            org.apache.flink.cdc.connectors.jdbc.enumerator.JdbcEnumeratorState>
            getEnumeratorCheckpointSerializer() {
        return new JdbcEnumeratorStateSerializer();
    }
}
