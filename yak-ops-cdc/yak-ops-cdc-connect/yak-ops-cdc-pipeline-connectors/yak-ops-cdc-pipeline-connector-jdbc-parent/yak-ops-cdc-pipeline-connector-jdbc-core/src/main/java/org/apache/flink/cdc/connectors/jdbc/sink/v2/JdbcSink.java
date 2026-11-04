package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import org.apache.flink.api.connector.sink2.StatefulSink;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.cdc.connectors.jdbc.dialect.JdbcSinkDialect;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.datasource.connections.JdbcConnectionProvider;
import org.apache.flink.connector.jdbc.internal.JdbcOutputSerializer;
import org.apache.flink.connector.jdbc.core.datastream.sink.writer.JdbcWriterState;
import org.apache.flink.connector.jdbc.core.datastream.sink.writer.JdbcWriterStateSerializer;
import org.apache.flink.core.io.SimpleVersionedSerializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/** Implementation class of the {@link StatefulSink} interface. */
public class JdbcSink<IN> implements StatefulSink<IN, JdbcWriterState> {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcSink.class);

    private final JdbcConnectionProvider connectionProvider;

    private final JdbcExecutionOptions executionOptions;

    private final RecordSerializationSchema<IN> serializationSchema;

    private final JdbcSinkDialect dialect;

    private final JdbcSinkConfig sinkConfig;

    public JdbcSink(
            JdbcExecutionOptions executionOptions,
            JdbcConnectionProvider connectionProvider,
            RecordSerializationSchema<IN> serializationSchema,
            JdbcSinkDialect dialect,
            JdbcSinkConfig sinkConfig) {
        this.executionOptions = executionOptions;
        this.connectionProvider = connectionProvider;
        this.serializationSchema = serializationSchema;
        this.dialect = dialect;
        this.sinkConfig = sinkConfig;
    }

    @Override
    public StatefulSinkWriter<IN, JdbcWriterState> createWriter(InitContext context)
            throws IOException {
        return restoreWriter(context, Collections.emptyList());
    }

    @Override
    public StatefulSinkWriter<IN, JdbcWriterState> restoreWriter(
            InitContext context, Collection<JdbcWriterState> collection) throws IOException {
        JdbcOutputSerializer<Object> outputSerializer =
                JdbcOutputSerializer.of(
                        context.createInputSerializer(), context.isObjectReuseEnabled());

        return new JdbcWriter<>(
                context,
                executionOptions,
                connectionProvider,
                outputSerializer,
                serializationSchema,
                dialect,
                sinkConfig);
    }

    @Override
    public SimpleVersionedSerializer<JdbcWriterState> getWriterStateSerializer() {
        return new JdbcWriterStateSerializer();
    }
}
