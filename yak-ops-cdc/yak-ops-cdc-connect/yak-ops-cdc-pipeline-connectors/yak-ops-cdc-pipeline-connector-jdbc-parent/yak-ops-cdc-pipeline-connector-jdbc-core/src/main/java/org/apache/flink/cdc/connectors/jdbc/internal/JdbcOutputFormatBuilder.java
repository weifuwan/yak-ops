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

package org.apache.flink.cdc.connectors.jdbc.internal;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.connectors.jdbc.internal.converter.JdbcRowConverter;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialect;
import org.apache.flink.cdc.connectors.jdbc.internal.executor.BatchedStatementExecutor;
import org.apache.flink.cdc.connectors.jdbc.internal.data.JdbcRowData;
import org.apache.flink.cdc.connectors.jdbc.internal.output.RichJdbcOutputFormat;
import org.apache.flink.cdc.connectors.jdbc.internal.data.RichJdbcRowData;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.datasource.connections.JdbcConnectionProvider;
import org.apache.flink.connector.jdbc.internal.JdbcOutputSerializer;
import org.apache.flink.connector.jdbc.internal.executor.JdbcBatchStatementExecutor;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.flink.cdc.connectors.jdbc.internal.data.RowKind.PK_ROW;
import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Assembles a single table's statements and Flink JDBC output format.
 *
 * <p>SQL semantics belong to the dialect; batching and retries remain in Flink JDBC.
 */
public class JdbcOutputFormatBuilder {
    private final JdbcExecutionOptions executionOptions;
    private final JdbcConnectionProvider connectionProvider;
    private final JdbcOutputSerializer<Object> outputSerializer;
    private final JdbcDialect dialect;
    private final JdbcRowConverter converter;

    public JdbcOutputFormatBuilder(
            JdbcExecutionOptions executionOptions,
            JdbcConnectionProvider connectionProvider,
            JdbcOutputSerializer<Object> outputSerializer,
            JdbcDialect dialect,
            JdbcRowConverter converter) {
        this.executionOptions = checkNotNull(executionOptions);
        this.connectionProvider = checkNotNull(connectionProvider);
        this.outputSerializer = checkNotNull(outputSerializer);
        this.dialect = checkNotNull(dialect);
        this.converter = checkNotNull(converter);
    }

    public RichJdbcOutputFormat build(TableId tableId, Schema schema) throws IOException {
        checkNotNull(tableId, "JDBC table id is required");
        checkNotNull(schema, "JDBC table schema is required");
        RichJdbcOutputFormat format =
                new RichJdbcOutputFormat(
                        connectionProvider,
                        executionOptions,
                        () -> createStatementExecutor(tableId, schema));
        try {
            format.open(outputSerializer);
            return format;
        } catch (IOException | RuntimeException failure) {
            // Flink JDBC may have established a connection before preparing statements failed.
            try {
                format.close();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private JdbcBatchStatementExecutor<JdbcRowData> createStatementExecutor(
            TableId tableId, Schema schema) {
        List<String> columns = schema.getColumnNames();
        List<String> deleteColumns =
                schema.primaryKeys().isEmpty() ? columns : schema.primaryKeys();
        List<String> deleteRowColumns =
                schema.primaryKeys().isEmpty()
                        ? columns
                        : columns.stream()
                                .filter(deleteColumns::contains)
                                .collect(Collectors.toList());

        JdbcBatchStatementExecutor<JdbcRowData> upserts =
                JdbcBatchStatementExecutor.simple(
                        dialect.getUpsertStatement(tableId, schema),
                        converter.createStatementBuilder(schema, columns, columns));
        JdbcBatchStatementExecutor<JdbcRowData> deletes =
                JdbcBatchStatementExecutor.simple(
                        dialect.getDeleteStatement(tableId, schema),
                        converter.createStatementBuilder(schema, deleteColumns, deleteRowColumns));
        return new BatchedStatementExecutor(
                upserts, deletes, createRowKeyExtractor(tableId, schema));
    }

    /** Extracts an immutable, schema-ordered primary key for batch reduction. */
    public static Function<JdbcRowData, JdbcRowData> createRowKeyExtractor(
            TableId tableId, Schema schema) {
        List<String> primaryKeys = schema.primaryKeys();
        if (primaryKeys.isEmpty()) {
            return row ->
                    new RichJdbcRowData.Builder()
                            .setRowKind(PK_ROW)
                            .setTableId(row.getTableId())
                            .setSchema(row.getSchema())
                            .setRows(row.getRows())
                            .build();
        }

        Schema pkOnlySchema =
                schema.copy(
                        schema.getColumns().stream()
                                .filter(column -> primaryKeys.contains(column.getName()))
                                .collect(Collectors.toList()));
        List<String> rowColumns = schema.getColumnNames();
        List<String> keyColumns = pkOnlySchema.getColumnNames();
        int[] keyIndexes = new int[keyColumns.size()];
        for (int i = 0; i < keyColumns.size(); i++) {
            keyIndexes[i] = rowColumns.indexOf(keyColumns.get(i));
            checkArgument(keyIndexes[i] >= 0, "Missing primary key %s", keyColumns.get(i));
        }

        return row -> {
            Object[] values = checkNotNull(row.getRows(), "JDBC row values must not be null");
            Object[] keyValues = new Object[keyIndexes.length];
            for (int i = 0; i < keyIndexes.length; i++) {
                keyValues[i] = values[keyIndexes[i]];
            }
            return new RichJdbcRowData.Builder()
                    .setRowKind(PK_ROW)
                    .setTableId(tableId)
                    .setSchema(pkOnlySchema)
                    .setRows(keyValues)
                    .build();
        };
    }
}
