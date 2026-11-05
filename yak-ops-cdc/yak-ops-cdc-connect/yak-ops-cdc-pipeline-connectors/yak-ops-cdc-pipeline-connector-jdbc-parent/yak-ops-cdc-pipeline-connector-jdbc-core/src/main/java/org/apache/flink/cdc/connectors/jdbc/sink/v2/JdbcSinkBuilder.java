package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialect;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.datasource.connections.JdbcConnectionProvider;

import static org.apache.flink.util.Preconditions.checkNotNull;

/** Builder to construct {@link JdbcSink}. */
@PublicEvolving
public class JdbcSinkBuilder<IN> {
    private JdbcExecutionOptions executionOptions;
    private JdbcConnectionOptions connectionOptions;
    private JdbcDialect dialect;
    private JdbcSinkConfig sinkConfig;
    private RecordSerializationSchema<IN> serializationSchema;

    public JdbcSinkBuilder() {
        this.executionOptions = JdbcExecutionOptions.defaults();
    }

    public JdbcSinkBuilder<IN> withExecutionOptions(JdbcExecutionOptions executionOptions) {
        this.executionOptions = checkNotNull(executionOptions, "executionOptions cannot be null");
        return this;
    }

    public JdbcSinkBuilder<IN> withDialect(JdbcDialect dialect) {
        this.dialect = checkNotNull(dialect, "dialect cannot be null");
        return this;
    }

    public JdbcSinkBuilder<IN> withSinkConfig(JdbcSinkConfig sinkConfig) {
        this.sinkConfig = checkNotNull(sinkConfig, "sink config cannot be null");
        return this;
    }

    public JdbcSinkBuilder<IN> withSerializationSchema(
            RecordSerializationSchema<IN> serializationSchema) {
        this.serializationSchema =
                checkNotNull(serializationSchema, "serializationSchema cannot be null");
        return this;
    }

    public JdbcSink<IN> buildAtLeastOnce(JdbcConnectionOptions connectionOptions) {
        checkNotNull(connectionOptions, "connectionOptions cannot be null");

        this.connectionOptions = connectionOptions;
        return build(null);
    }

    public JdbcSink<IN> buildAtLeastOnce(JdbcConnectionProvider connectionProvider) {
        checkNotNull(connectionProvider, "connectionProvider cannot be null");

        this.connectionOptions = null;
        return build(checkNotNull(connectionProvider, "connectionProvider cannot be null"));
    }

    private JdbcSink<IN> build(JdbcConnectionProvider connectionProvider) {

        return new JdbcSink<>(
                executionOptions,
                connectionOptions,
                connectionProvider,
                serializationSchema,
                dialect,
                sinkConfig);
    }
}
