package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.datasource.connections.JdbcConnectionProvider;
import org.apache.flink.connector.jdbc.internal.JdbcOutputFormat;
import org.apache.flink.connector.jdbc.internal.executor.JdbcBatchStatementExecutor;

import javax.annotation.Nonnull;

/**
 * Merely a wrapper of {@code JdbcOutputFormat<Object, JdbcRowData,
 * JdbcBatchStatementExecutor<JdbcRowData>>} to avoid typing such a long generic type arguments
 * again and again.
 */
public class RichJdbcOutputFormat
        extends JdbcOutputFormat<Object, JdbcRowData, JdbcBatchStatementExecutor<JdbcRowData>> {
    public RichJdbcOutputFormat(
            @Nonnull JdbcConnectionProvider connectionProvider,
            @Nonnull JdbcExecutionOptions executionOptions,
            @Nonnull
                    StatementExecutorFactory<JdbcBatchStatementExecutor<JdbcRowData>>
                            statementExecutorFactory) {
        super(connectionProvider, executionOptions, statementExecutorFactory);
    }
}
