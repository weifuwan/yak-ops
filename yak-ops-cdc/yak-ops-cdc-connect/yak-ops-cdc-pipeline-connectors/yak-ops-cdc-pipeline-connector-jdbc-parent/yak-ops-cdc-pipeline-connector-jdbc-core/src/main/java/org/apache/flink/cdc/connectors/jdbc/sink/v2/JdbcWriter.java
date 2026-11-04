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

package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import org.apache.flink.cdc.connectors.jdbc.internal.data.JdbcRowData;
import org.apache.flink.cdc.connectors.jdbc.internal.data.RowKind;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.StatefulSink;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.cdc.connectors.jdbc.internal.JdbcOutputFormatBuilder;
import org.apache.flink.cdc.connectors.jdbc.internal.converter.JdbcRowConverter;
import org.apache.flink.cdc.connectors.jdbc.internal.writer.JdbcTableWriterRegistry;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialect;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.datasource.connections.JdbcConnectionProvider;
import org.apache.flink.connector.jdbc.datasource.connections.SimpleJdbcConnectionProvider;
import org.apache.flink.connector.jdbc.internal.JdbcOutputSerializer;
import org.apache.flink.connector.jdbc.core.datastream.sink.writer.JdbcWriterState;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Flink Sink V2 adapter that coordinates the lifecycle of table-scoped JDBC writers.
 *
 * <p>SQL assembly and parameter binding are deliberately delegated to the internal builder.
 */
public class JdbcWriter<IN> implements StatefulSink.StatefulSinkWriter<IN, JdbcWriterState> {
    private final JdbcExecutionOptions executionOptions;
    private final JdbcConnectionOptions connectionOptions;
    private final JdbcConnectionProvider suppliedConnectionProvider;
    private final JdbcOutputSerializer<Object> outputSerializer;
    private final RecordSerializationSchema<IN> serializationSchema;
    private final JdbcDialect dialect;
    private final JdbcRowConverter rowConverter;

    private final JdbcTableWriterRegistry<JdbcTableWriter> tableWriters;

    public JdbcWriter(
            Sink.InitContext initContext,
            JdbcExecutionOptions executionOptions,
            JdbcConnectionOptions connectionOptions,
            JdbcConnectionProvider suppliedConnectionProvider,
            JdbcOutputSerializer<Object> outputSerializer,
            RecordSerializationSchema<IN> serializationSchema,
            JdbcDialect dialect,
            JdbcSinkConfig sinkConfig) {
        checkNotNull(initContext, "initContext must be defined");
        this.executionOptions = checkNotNull(executionOptions);
        checkArgument(
                connectionOptions != null || suppliedConnectionProvider != null,
                "JDBC connection options or provider must be defined");
        this.connectionOptions = connectionOptions;
        this.suppliedConnectionProvider = suppliedConnectionProvider;
        this.outputSerializer = checkNotNull(outputSerializer);
        this.serializationSchema = checkNotNull(serializationSchema);
        this.dialect = checkNotNull(dialect);
        checkNotNull(sinkConfig, "sinkConfig must be defined");
        this.rowConverter = checkNotNull(dialect.createRowConverter(), "JDBC row converter is required");
        int maxOpenTables =
                connectionOptions == null ? 1 : sinkConfig.getWriteMaxOpenTables();
        this.tableWriters =
                new JdbcTableWriterRegistry<>(
                        maxOpenTables, this::createTableWriter, JdbcTableWriter::flush);
    }

    @Override
    public List<JdbcWriterState> snapshotState(long checkpointId) {
        // Flink JDBC's at-least-once writer does not snapshot transactional state.
        return Collections.emptyList();
    }

    @Override
    public void write(IN event, Context context) throws IOException {
        for (JdbcRowData row : serializationSchema.serialize(event)) {
            TableId tableId = row.getTableId();
            if (RowKind.SCHEMA_CHANGE.is(row.getRowKind())) {
                flush(false);
                tableWriters.invalidate(tableId);
            } else {
                tableWriters.getOrCreate(tableId, row.getSchema()).write(row);
            }
        }
    }

    private JdbcTableWriter createTableWriter(TableId tableId, Schema schema)
            throws IOException {
        JdbcConnectionProvider provider =
                connectionOptions != null
                        ? new SimpleJdbcConnectionProvider(connectionOptions)
                        : suppliedConnectionProvider;
        JdbcOutputFormatBuilder builder =
                new JdbcOutputFormatBuilder(
                        executionOptions, provider, outputSerializer, dialect, rowConverter);
        return new JdbcTableWriter(tableId, schema, builder);
    }

    @Override
    public void flush(boolean endOfInput) throws IOException {
        IOException failure = null;
        try {
            tableWriters.flushAll();
        } catch (IOException e) {
            failure = e;
        } finally {
            if (endOfInput) {
                try {
                    tableWriters.close();
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() throws Exception {
        flush(true);
    }

}
