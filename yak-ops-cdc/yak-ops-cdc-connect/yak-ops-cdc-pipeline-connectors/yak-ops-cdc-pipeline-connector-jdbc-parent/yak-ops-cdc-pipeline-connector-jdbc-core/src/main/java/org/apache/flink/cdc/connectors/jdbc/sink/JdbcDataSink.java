package org.apache.flink.cdc.connectors.jdbc.sink;

import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.common.sink.DataSink;
import org.apache.flink.cdc.common.sink.EventSinkProvider;
import org.apache.flink.cdc.common.sink.FlinkSinkProvider;
import org.apache.flink.cdc.common.sink.MetadataApplier;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.cdc.connectors.jdbc.dialect.JdbcSinkDialect;
import org.apache.flink.cdc.connectors.jdbc.sink.v2.EventRecordSerializationSchema;
import org.apache.flink.cdc.connectors.jdbc.sink.v2.JdbcSink;
import org.apache.flink.cdc.connectors.jdbc.sink.v2.JdbcSinkBuilder;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;

import java.io.Serializable;

/** Writing data to JDBC-like sinks. */
public class JdbcDataSink implements DataSink, Serializable {
    private final JdbcSinkDialect dialect;
    private final JdbcSinkConfig sinkConfig;

    public JdbcDataSink(JdbcSinkDialect dialect, JdbcSinkConfig sinkConfig) {
        this.dialect = dialect;
        this.sinkConfig = sinkConfig;
    }

    @Override
    public EventSinkProvider getEventSinkProvider() {
        JdbcConnectionOptions connectionOptions =
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(sinkConfig.getConnUrl())
                        .withUsername(sinkConfig.getUsername())
                        .withPassword(sinkConfig.getPassword())
                        .withDriverName(sinkConfig.getDriverClassName())
                        .build();

        JdbcSink<Event> jdbcSink =
                new JdbcSinkBuilder<Event>()
                        .withExecutionOptions(
                                JdbcExecutionOptions.builder()
                                        .withBatchSize(sinkConfig.getWriteBatchSize())
                                        .withBatchIntervalMs(sinkConfig.getWriteBatchIntervalMs())
                                        .withMaxRetries(sinkConfig.getWriteMaxRetries())
                                        .build())
                        .withSerializationSchema(new EventRecordSerializationSchema())
                        .withDialect(dialect)
                        .withSinkConfig(sinkConfig)
                        .buildAtLeastOnce(connectionOptions);

        return FlinkSinkProvider.of(jdbcSink);
    }

    @Override
    public MetadataApplier getMetadataApplier() {
        return new JdbcMetadataApplier(dialect);
    }
}
