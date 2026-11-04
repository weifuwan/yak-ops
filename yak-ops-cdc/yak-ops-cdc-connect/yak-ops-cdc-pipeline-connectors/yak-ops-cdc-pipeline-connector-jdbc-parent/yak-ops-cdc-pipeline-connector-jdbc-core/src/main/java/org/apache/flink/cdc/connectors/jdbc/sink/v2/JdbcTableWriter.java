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

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.connectors.jdbc.internal.JdbcOutputFormatBuilder;

import java.io.IOException;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/** Owns the JDBC output format and statements for exactly one physical target table. */
public class JdbcTableWriter implements AutoCloseable {
    private final TableId tableId;
    private final Schema schema;
    private final RichJdbcOutputFormat outputFormat;

    public JdbcTableWriter(
            TableId tableId, Schema schema, JdbcOutputFormatBuilder outputFormatBuilder)
            throws IOException {
        this.tableId = checkNotNull(tableId);
        this.schema = checkNotNull(schema);
        this.outputFormat = checkNotNull(outputFormatBuilder).build(tableId, schema);
    }

    public TableId getTableId() {
        return tableId;
    }

    public Schema getSchema() {
        return schema;
    }

    public void write(JdbcRowData rowData) throws IOException {
        checkArgument(tableId.equals(rowData.getTableId()), "JDBC table writer received wrong table");
        outputFormat.writeRecord(rowData);
        if (!rowData.hasPrimaryKey()) {
            // Keep the pre-refactor non-PK flush behavior until append-only semantics are defined.
            outputFormat.flush();
        }
    }

    public void flush() throws IOException {
        outputFormat.flush();
    }

    @Override
    public void close() {
        outputFormat.close();
    }
}
