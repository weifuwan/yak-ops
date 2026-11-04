/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
