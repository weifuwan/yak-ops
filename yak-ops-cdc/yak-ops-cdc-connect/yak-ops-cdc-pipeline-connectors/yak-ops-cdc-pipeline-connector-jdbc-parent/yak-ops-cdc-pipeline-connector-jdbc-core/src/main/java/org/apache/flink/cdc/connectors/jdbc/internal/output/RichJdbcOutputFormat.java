package org.apache.flink.cdc.connectors.jdbc.internal.output;

import org.apache.flink.cdc.connectors.jdbc.internal.data.JdbcRowData;
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

    private final JdbcConnectionProvider connectionProvider;

    public RichJdbcOutputFormat(
            @Nonnull JdbcConnectionProvider connectionProvider,
            @Nonnull JdbcExecutionOptions executionOptions,
            @Nonnull
                    StatementExecutorFactory<JdbcBatchStatementExecutor<JdbcRowData>>
                            statementExecutorFactory) {
        super(connectionProvider, executionOptions, statementExecutorFactory);
        this.connectionProvider = connectionProvider;
    }

    /**
     * Flink JDBC 3.3.0 can exit {@code JdbcOutputFormat.close()} early when its final
     * batch flush fails, before reaching {@code closeConnection()}. Always release the
     * connection on that failure path, preserving the original exception and any
     * cleanup exception as suppressed.
     */
    @Override
    public synchronized void close() {
        try {
            super.close();
        } catch (RuntimeException | Error failure) {
            try {
                connectionProvider.closeConnection();
            } catch (RuntimeException | Error closeFailure) {
                if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }
    }
}
